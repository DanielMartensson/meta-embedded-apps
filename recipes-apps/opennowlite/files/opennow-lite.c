/*
 * opennow-lite.c - OpenNOWLite stage 1: its own Wayland window, fed by
 * GStreamer, with its own keyboard and pointer pipeline.
 *
 * The window is an xdg-shell toplevel created here, not by the video sink and
 * not by opennow-qt. waylandsink draws into that surface: the wl_display goes
 * to the sink through GstWlDisplayHandleContextType and the surface through the
 * GstVideoOverlay window handle - the same hand-off this layer uses for YtGst -
 * so the picture fills this program's window while this program keeps the
 * seat's keyboard and pointer events on the same connection.
 *
 * Stage 1 plays a test pattern, so the window and the input path can be proven
 * with no session at all. Stage 2 replaces videotestsrc with the native
 * streamer's encoded frames and submits the input through
 * opennow_streamer_submit_key / opennow_streamer_submit_mouse_*.
 *
 * Markers (grep -a):
 *   opennowlite: window ready
 *   opennowlite: pipeline playing
 *   opennowlite-input: key ...
 *   opennowlite-input: pointer ...
 *   opennowlite: pipeline error
 */

#define _GNU_SOURCE
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <wayland-client.h>

#include <gst/gst.h>
#include <gst/video/videooverlay.h>
#include <gst/wayland/gstwlcontext.h>

#include "xdg-shell-client-protocol.h"

static volatile sig_atomic_t running = 1;

static void on_signal(int sig)
{
    (void)sig;
    running = 0;
}

struct lite {
    struct wl_display *display;
    struct wl_registry *registry;
    struct wl_compositor *compositor;
    struct wl_shm *shm;
    struct xdg_wm_base *wm_base;
    struct wl_seat *seat;
    struct wl_keyboard *keyboard;
    struct wl_pointer *pointer;
    struct wl_surface *surface;
    struct xdg_surface *xdg_surface;
    struct xdg_toplevel *toplevel;
    struct wl_surface *pointer_surface;
    double pointer_x;
    double pointer_y;
    bool configured;
    bool fullscreen;
    int width;
    int height;
    GstElement *pipeline;
    GstElement *sink;
    GstBus *bus;
    struct timespec last_motion;
    unsigned long keys;
    unsigned long motions;
};

static struct lite app;

static long ms_since(const struct timespec *then)
{
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (now.tv_sec - then->tv_sec) * 1000L +
           (now.tv_nsec - then->tv_nsec) / 1000000L;
}

/* ---- xdg-shell ---- */

static void wm_base_ping(void *data, struct xdg_wm_base *wm_base, uint32_t serial)
{
    (void)data;
    xdg_wm_base_pong(wm_base, serial);
}

static const struct xdg_wm_base_listener wm_base_listener = {
    .ping = wm_base_ping,
};

static void xdg_surface_configure(void *data, struct xdg_surface *surface, uint32_t serial)
{
    struct lite *a = data;
    xdg_surface_ack_configure(surface, serial);
    a->configured = true;
}

static const struct xdg_surface_listener xdg_surface_listener = {
    .configure = xdg_surface_configure,
};

static void toplevel_configure(void *data, struct xdg_toplevel *toplevel,
                               int32_t width, int32_t height, struct wl_array *states)
{
    (void)data; (void)toplevel; (void)width; (void)height; (void)states;
}

static void toplevel_close(void *data, struct xdg_toplevel *toplevel)
{
    (void)toplevel;
    struct lite *a = data;
    fprintf(stderr, "opennowlite: window close requested\n");
    running = 0;
}

static const struct xdg_toplevel_listener toplevel_listener = {
    .configure = toplevel_configure,
    .close = toplevel_close,
};

/* ---- keyboard ---- */

static void kb_keymap(void *data, struct wl_keyboard *kb, uint32_t format, int32_t fd, uint32_t size)
{
    (void)data; (void)kb; (void)format; (void)size;
    if (fd >= 0)
        close(fd);
}

static void kb_enter(void *data, struct wl_keyboard *kb, uint32_t serial,
                     struct wl_surface *surface, struct wl_array *keys)
{
    (void)data; (void)kb; (void)serial; (void)surface; (void)keys;
    fprintf(stderr, "opennowlite-input: keyboard enter\n");
}

static void kb_leave(void *data, struct wl_keyboard *kb, uint32_t serial, struct wl_surface *surface)
{
    (void)data; (void)kb; (void)serial; (void)surface;
    fprintf(stderr, "opennowlite-input: keyboard leave\n");
}

static void kb_key(void *data, struct wl_keyboard *kb, uint32_t serial, uint32_t time,
                   uint32_t key, uint32_t state)
{
    (void)kb; (void)serial; (void)time;
    struct lite *a = data;
    a->keys++;
    /* Wayland key codes are evdev codes plus 8. */
    fprintf(stderr, "opennowlite-input: key code=%u evdev=%u state=%u\n",
            key, key >= 8 ? key - 8 : 0, state);
}

static void kb_modifiers(void *data, struct wl_keyboard *kb, uint32_t serial,
                         uint32_t depressed, uint32_t latched, uint32_t locked, uint32_t group)
{
    (void)data; (void)kb; (void)serial;
    (void)depressed; (void)latched; (void)locked; (void)group;
}

static void kb_repeat(void *data, struct wl_keyboard *kb, int32_t rate, int32_t delay)
{
    (void)data; (void)kb; (void)rate; (void)delay;
}

static const struct wl_keyboard_listener keyboard_listener = {
    .keymap = kb_keymap,
    .enter = kb_enter,
    .leave = kb_leave,
    .key = kb_key,
    .modifiers = kb_modifiers,
    .repeat_info = kb_repeat,
};

/* ---- pointer ---- */

static void ptr_enter(void *data, struct wl_pointer *ptr, uint32_t serial,
                      struct wl_surface *surface, wl_fixed_t sx, wl_fixed_t sy)
{
    (void)ptr; (void)serial;
    struct lite *a = data;
    a->pointer_surface = surface;
    a->pointer_x = wl_fixed_to_double(sx);
    a->pointer_y = wl_fixed_to_double(sy);
    fprintf(stderr, "opennowlite-input: pointer enter x=%.1f y=%.1f\n",
            a->pointer_x, a->pointer_y);
}

static void ptr_leave(void *data, struct wl_pointer *ptr, uint32_t serial, struct wl_surface *surface)
{
    (void)ptr; (void)serial; (void)surface;
    struct lite *a = data;
    a->pointer_surface = NULL;
    fprintf(stderr, "opennowlite-input: pointer leave\n");
}

static void ptr_motion(void *data, struct wl_pointer *ptr, uint32_t time, wl_fixed_t sx, wl_fixed_t sy)
{
    (void)ptr; (void)time;
    struct lite *a = data;
    a->pointer_x = wl_fixed_to_double(sx);
    a->pointer_y = wl_fixed_to_double(sy);
    a->motions++;
    /* Rate-limit the log; the position itself is kept on every event. */
    if (ms_since(&a->last_motion) >= 100) {
        clock_gettime(CLOCK_MONOTONIC, &a->last_motion);
        fprintf(stderr, "opennowlite-input: pointer motion x=%.1f y=%.1f\n",
                a->pointer_x, a->pointer_y);
    }
}

static void ptr_button(void *data, struct wl_pointer *ptr, uint32_t serial, uint32_t time,
                       uint32_t button, uint32_t state)
{
    (void)ptr; (void)serial; (void)time;
    (void)data;
    fprintf(stderr, "opennowlite-input: pointer button=%u state=%u\n", button, state);
}

static void ptr_axis(void *data, struct wl_pointer *ptr, uint32_t time, uint32_t axis, wl_fixed_t value)
{
    (void)ptr; (void)time; (void)data;
    fprintf(stderr, "opennowlite-input: pointer axis=%u value=%.2f\n",
            axis, wl_fixed_to_double(value));
}

static void ptr_frame(void *data, struct wl_pointer *ptr)
{
    (void)data; (void)ptr;
}

static void ptr_axis_source(void *data, struct wl_pointer *ptr, uint32_t source)
{
    (void)data; (void)ptr; (void)source;
}

static void ptr_axis_stop(void *data, struct wl_pointer *ptr, uint32_t time, uint32_t axis)
{
    (void)data; (void)ptr; (void)time; (void)axis;
}

static void ptr_axis_discrete(void *data, struct wl_pointer *ptr, uint32_t axis, int32_t discrete)
{
    (void)data; (void)ptr; (void)axis; (void)discrete;
}

static void ptr_axis_value120(void *data, struct wl_pointer *ptr, uint32_t axis, int32_t value120)
{
    (void)data; (void)ptr; (void)axis; (void)value120;
}

static const struct wl_pointer_listener pointer_listener = {
    .enter = ptr_enter,
    .leave = ptr_leave,
    .motion = ptr_motion,
    .button = ptr_button,
    .axis = ptr_axis,
    .frame = ptr_frame,
    .axis_source = ptr_axis_source,
    .axis_stop = ptr_axis_stop,
    .axis_discrete = ptr_axis_discrete,
    .axis_value120 = ptr_axis_value120,
};

/* ---- seat ---- */

static void seat_capabilities(void *data, struct wl_seat *seat, uint32_t caps)
{
    struct lite *a = data;
    if ((caps & WL_SEAT_CAPABILITY_KEYBOARD) && !a->keyboard) {
        a->keyboard = wl_seat_get_keyboard(seat);
        wl_keyboard_add_listener(a->keyboard, &keyboard_listener, a);
        fprintf(stderr, "opennowlite: seat has a keyboard\n");
    } else if (!(caps & WL_SEAT_CAPABILITY_KEYBOARD) && a->keyboard) {
        wl_keyboard_destroy(a->keyboard);
        a->keyboard = NULL;
    }
    if ((caps & WL_SEAT_CAPABILITY_POINTER) && !a->pointer) {
        a->pointer = wl_seat_get_pointer(seat);
        wl_pointer_add_listener(a->pointer, &pointer_listener, a);
        fprintf(stderr, "opennowlite: seat has a pointer\n");
    } else if (!(caps & WL_SEAT_CAPABILITY_POINTER) && a->pointer) {
        wl_pointer_destroy(a->pointer);
        a->pointer = NULL;
    }
}

static void seat_name(void *data, struct wl_seat *seat, const char *name)
{
    (void)data; (void)seat;
    fprintf(stderr, "opennowlite: seat name %s\n", name);
}

static const struct wl_seat_listener seat_listener = {
    .capabilities = seat_capabilities,
    .name = seat_name,
};

/* ---- registry ---- */

static void registry_global(void *data, struct wl_registry *registry, uint32_t name,
                            const char *interface, uint32_t version)
{
    struct lite *a = data;

    if (strcmp(interface, wl_compositor_interface.name) == 0) {
        a->compositor = wl_registry_bind(registry, name, &wl_compositor_interface,
                                         version < 4 ? version : 4);
    } else if (strcmp(interface, wl_shm_interface.name) == 0) {
        a->shm = wl_registry_bind(registry, name, &wl_shm_interface, 1);
    } else if (strcmp(interface, xdg_wm_base_interface.name) == 0) {
        a->wm_base = wl_registry_bind(registry, name, &xdg_wm_base_interface, 1);
        xdg_wm_base_add_listener(a->wm_base, &wm_base_listener, a);
    } else if (strcmp(interface, wl_seat_interface.name) == 0) {
        a->seat = wl_registry_bind(registry, name, &wl_seat_interface,
                                   version < 5 ? version : 5);
        wl_seat_add_listener(a->seat, &seat_listener, a);
    }
}

static void registry_global_remove(void *data, struct wl_registry *registry, uint32_t name)
{
    (void)data; (void)registry; (void)name;
}

static const struct wl_registry_listener registry_listener = {
    .global = registry_global,
    .global_remove = registry_global_remove,
};

/* ---- window ---- */

static bool create_window(struct lite *a)
{
    a->surface = wl_compositor_create_surface(a->compositor);
    if (!a->surface)
        return false;

    a->xdg_surface = xdg_wm_base_get_xdg_surface(a->wm_base, a->surface);
    if (!a->xdg_surface)
        return false;
    xdg_surface_add_listener(a->xdg_surface, &xdg_surface_listener, a);

    a->toplevel = xdg_surface_get_toplevel(a->xdg_surface);
    if (!a->toplevel)
        return false;
    xdg_toplevel_add_listener(a->toplevel, &toplevel_listener, a);
    xdg_toplevel_set_title(a->toplevel, "OpenNOWLite");
    xdg_toplevel_set_app_id(a->toplevel, "opennowlite");

    wl_surface_commit(a->surface);
    wl_display_flush(a->display);

    /* The sink needs a configured surface before it renders its first frame. */
    while (running && !a->configured) {
        if (wl_display_dispatch(a->display) < 0) {
            fprintf(stderr, "opennowlite: display dispatch failed: %s\n", strerror(errno));
            return false;
        }
    }
    if (!running)
        return false;

    if (a->fullscreen)
        xdg_toplevel_set_fullscreen(a->toplevel, NULL);

    fprintf(stderr, "opennowlite: window ready surface=%p app_id=opennowlite\n",
            (void *)a->surface);
    return true;
}

/* ---- pipeline ---- */

static bool start_pipeline(struct lite *a, const char *description)
{
    GError *error = NULL;

    a->pipeline = gst_parse_launch(description, &error);
    if (!a->pipeline) {
        fprintf(stderr, "opennowlite: pipeline parse failed: %s\n",
                error ? error->message : "unknown");
        if (error)
            g_error_free(error);
        return false;
    }

    a->sink = gst_bin_get_by_name(GST_BIN(a->pipeline), "sink");
    if (!a->sink) {
        fprintf(stderr, "opennowlite: pipeline has no element named sink\n");
        return false;
    }
    if (!GST_IS_VIDEO_OVERLAY(a->sink)) {
        fprintf(stderr, "opennowlite: sink has no GstVideoOverlay interface\n");
        return false;
    }

    /* The display goes in first: without it the sink connects on its own and
     * the picture lands in a window this program does not own. */
    GstContext *context = gst_wl_display_handle_context_new(a->display);
    gst_element_set_context(a->sink, context);
    gst_context_unref(context);

    gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(a->sink),
                                        (guintptr)a->surface);
    /* An external window has no size of its own: waylandsink refuses the first
     * frame with "Window has no size set" until the render rectangle says how
     * big the picture is. This also sets the toplevel's size, because the
     * sink's buffers are the only thing this surface ever attaches. */
    gst_video_overlay_set_render_rectangle(GST_VIDEO_OVERLAY(a->sink),
                                           0, 0, a->width, a->height);

    a->bus = gst_element_get_bus(a->pipeline);
    if (gst_element_set_state(a->pipeline, GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE) {
        fprintf(stderr, "opennowlite: pipeline refused PLAYING\n");
        return false;
    }

    fprintf(stderr, "opennowlite: pipeline playing\n");
    return true;
}

static void stop_pipeline(struct lite *a)
{
    if (a->pipeline) {
        gst_element_set_state(a->pipeline, GST_STATE_NULL);
        gst_object_unref(a->pipeline);
        a->pipeline = NULL;
    }
    if (a->bus) {
        gst_object_unref(a->bus);
        a->bus = NULL;
    }
    if (a->sink) {
        gst_object_unref(a->sink);
        a->sink = NULL;
    }
}

static bool drain_bus(struct lite *a)
{
    if (!a->bus)
        return true;

    GstMessage *msg;
    while ((msg = gst_bus_pop(a->bus)) != NULL) {
        switch (GST_MESSAGE_TYPE(msg)) {
        case GST_MESSAGE_ERROR: {
            GError *error = NULL;
            gchar *debug = NULL;
            gst_message_parse_error(msg, &error, &debug);
            fprintf(stderr, "opennowlite: pipeline error: %s\n",
                    error ? error->message : "unknown");
            if (debug)
                fprintf(stderr, "opennowlite: pipeline debug: %s\n", debug);
            g_clear_error(&error);
            g_free(debug);
            gst_message_unref(msg);
            return false;
        }
        case GST_MESSAGE_EOS:
            fprintf(stderr, "opennowlite: pipeline end of stream\n");
            gst_message_unref(msg);
            return false;
        default:
            break;
        }
        gst_message_unref(msg);
    }
    return true;
}

/* ---- main ---- */

static void usage(const char *argv0)
{
    fprintf(stderr,
            "usage: %s [--fullscreen] [--width N] [--height N] [--pipeline DESC]\n"
            "  stage 1 plays a test pattern in the app's own Wayland window;\n"
            "  the pipeline must contain a waylandsink named sink.\n",
            argv0);
}

int main(int argc, char **argv)
{
    const char *pipeline =
        "videotestsrc is-live=true ! videoconvert ! waylandsink name=sink sync=false";
    bool fullscreen = false;
    int width = 1280;
    int height = 720;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--fullscreen") == 0) {
            fullscreen = true;
        } else if (strcmp(argv[i], "--width") == 0 && i + 1 < argc) {
            width = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--height") == 0 && i + 1 < argc) {
            height = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--pipeline") == 0 && i + 1 < argc) {
            pipeline = argv[++i];
        } else if (strcmp(argv[i], "--help") == 0) {
            usage(argv[0]);
            return 0;
        } else {
            fprintf(stderr, "opennowlite: unknown argument %s\n", argv[i]);
            usage(argv[0]);
            return 2;
        }
    }
    if (width < 64 || height < 64) {
        fprintf(stderr, "opennowlite: refusing a %dx%d window\n", width, height);
        return 2;
    }

    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);
    clock_gettime(CLOCK_MONOTONIC, &app.last_motion);

    gst_init(&argc, &argv);

    app.display = wl_display_connect(NULL);
    if (!app.display) {
        fprintf(stderr, "opennowlite: no Wayland display (WAYLAND_DISPLAY=%s)\n",
                getenv("WAYLAND_DISPLAY") ? getenv("WAYLAND_DISPLAY") : "(unset)");
        return 1;
    }
    app.registry = wl_display_get_registry(app.display);
    wl_registry_add_listener(app.registry, &registry_listener, &app);
    if (wl_display_roundtrip(app.display) < 0) {
        fprintf(stderr, "opennowlite: registry roundtrip failed\n");
        return 1;
    }
    if (!app.compositor || !app.wm_base) {
        fprintf(stderr, "opennowlite: compositor has no xdg-shell\n");
        return 1;
    }
    app.fullscreen = fullscreen;
    app.width = width;
    app.height = height;

    if (!create_window(&app))
        return 1;
    if (!start_pipeline(&app, pipeline))
        return 1;

    while (running) {
        if (!drain_bus(&app))
            break;

        wl_display_dispatch_pending(app.display);
        wl_display_flush(app.display);

        struct pollfd pfd = {
            .fd = wl_display_get_fd(app.display),
            .events = POLLIN,
        };
        int ready = poll(&pfd, 1, 50);
        if (ready < 0 && errno != EINTR)
            break;
        if (ready > 0 && (pfd.revents & POLLIN)) {
            if (wl_display_prepare_read(app.display) == 0) {
                wl_display_read_events(app.display);
                wl_display_dispatch_pending(app.display);
            } else {
                wl_display_dispatch_pending(app.display);
            }
        }
    }

    fprintf(stderr, "opennowlite: stopping keys=%lu motions=%lu\n",
            app.keys, app.motions);
    stop_pipeline(&app);
    wl_display_disconnect(app.display);
    return 0;
}
