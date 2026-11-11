SUMMARY = "FTEQW - the FTE Quake engine, built as a GLES-only client for the seat"
DESCRIPTION = "FTEQW is the FTE engine family: one codebase that runs Quake, \
QuakeWorld, Hexen and Q3 with a software, OpenGL or Vulkan renderer. This recipe \
builds exactly one of those: the plain GL renderer target (make gl-rel) compiled \
against the seat's GLES 3.1 stack. The board carries EGL, GLES1/2/3 and the Vivante \
GAL/VSC driver and has no desktop GL at all (no libGL.so, EGL_CLIENT_APIS is OpenGL_ES \
and OpenVG only, eglBindAPI(EGL_OPENGL_API) answers EGL_BAD_PARAMETER), so the build \
defines GLESONLY, GLSLONLY and FTE_GLES and takes every GL entry point at runtime \
through SDL_GL_GetProcAddress. The game data this engine wants is the LibreQuake \
payload the librequake recipe installs at /usr/share/librequake/id1 beside it, \
which is why the two recipes live in the same layer."
HOMEPAGE = "https://github.com/fte-team/fteqw"
BUGTRACKER = "https://github.com/fte-team/fteqw/issues"

LICENSE = "GPL-2.0-or-later"
LIC_FILES_CHKSUM = "file://engine/LICENSE;md5=79c2cc4426b9e6f3b9098f02eb47aac1"

# Pin: upstream's master tip at the time this recipe was written, f09ac7cc
# "Merge pull request #458 from Xylemon/master" (27 Sep 2026). Pinned rather
# than left at HEAD because the local patch below sits against this exact
# tree: 0001 touches the #if at gl/glquake.h:38, and a moved HEAD would have
# to be re-checked against it. See the README in this directory for the record
# of why this engine and this renderer were chosen (the seat has no desktop
# GL, so QuakeSpasm and the vkQuake build that used to be in this layer are
# out).
SRC_URI = "git://github.com/fte-team/fteqw.git;protocol=https;branch=master \
           file://0001-use-the-gles2-headers-instead-of-the-desktop-gl-ones.patch \
          "
SRCREV = "f09ac7cccd2d713ddf881d3f1324703082c2cfe4"

# FTEQW keeps no version file to read a release out of, so the deb version is
# the pin plus the pinned PKGR below, like every other recipe here.
PV = "0.0+git${SRCPV}"

# PKGR: pinned, like every other recipe in this layer, so the build counter
# does not reset and the deb version stays comparable across rebuilds.
PKGR = "r0.01"

S = "${WORKDIR}/git"

# What the binary links against. FTE's fteqw config (config_fteqw.h, the
# header's default) turns the feature set on (png+jpeg screenshots, freetype
# for truetype text, opus+speex for voice chat, ogg/vorbis for the soundtrack),
# and every one of those already sits in the seat's /usr/lib: libpng16.so.16,
# libjpeg.so.62, libfreetype.so.6, libopus.so.0, libspeex.so.1,
# libspeexdsp.so.1, libogg.so, libvorbis.so, libvorbisfile.so. Nothing new has
# to go into the image for this engine, and the same names go into RDEPENDS so
# the deb keeps the seat honest about what the binary needs. The OE recipes
# carry the lib names (libopus, not opus; libogg and libvorbis, not vorbis),
# which is what DEPENDS spells out. alsa-lib is runtime only: SDL's alsa driver
# opens snd_pcm_open("default"), which is what meta-watermelon-wine's
# alsa-state-stm32mp2.bbappend adds the pcm.!default stanza for.
DEPENDS = "libsdl2 libvorbis libogg libpng libjpeg-turbo freetype libopus speex speexdsp zlib"
RDEPENDS:${PN} = "libsdl2 libvorbis libogg libpng libjpeg-turbo freetype libopus speex speexdsp zlib alsa-lib"

# FTEQW has no configure script: the Makefile's FTE_CONFIG names a build-config
# header (engine/common/config_<name>.h) that the compiler itself preprocesses
# at line 131, and the defines it finds there are what turn features and link
# lines on or off. fteqw is that header's own default (FTE_CONFIG?=fteqw at
# engine/Makefile:128), and it is the one the Makefile's object sets were written
# against: every feature its COMPILE_OPTS asks for has an object behind it, which
# is the whole reason a leaner config cannot be used here. Tried and ruled out:
# config_minimal.h leaves RTLIGHTS off, gl_shadow.c's body is then
# "#if defined(RTLIGHTS) && !defined(SERVERONLY)" and compiles to nothing, and
# gl/gl_rlight.c:756 references the r_shadow_realtime_world/r_shadow_realtime_dlight
# cvars it defines outside any RTLIGHTS guard, so the link stops with
#   undefined reference to `r_shadow_realtime_world'
# (same for r_shadow_shadowmapping, r_shadow_raytrace, CategorizePlane and
# Mod_Batches_SplitLightmaps). config_fteqw_noweb.h leaves PACKAGEMANAGER on
# without WEBCLIENT, and engine/client/m_download.c then declares the struct
# without the members it uses - it reads
#   m_download.c:868:134: error: 'package_t' {aka 'struct package_s'} has no member named 'curdownload'
# and 'doautoupdate'/'pm_pendingprompts' undeclared. A third config of our own
# would be the way to lean further; that is not what this recipe does.
#
# What fteqw does give the seat is what it needs: AVAIL_OGGVORBIS, which is what
# plays LibreQuake's track02..11.ogg, and its COMPILE_OPTS ask the makefile for
# -DLINK_VORBISFILE, so the decoder links against the seat's libvorbisfile.so
# instead of being cut out (config_minimal.h has -DNO_VORBISFILE there instead).
# WEBCLIENT costs no extra library either: the Makefile has no curl link at all,
# FTE's downloader in engine/http/ is its own.
#
# The GLES half is not a config header either: GLESONLY, GLSLONLY and FTE_GLES
# come in through CFLAGS, because the renderer reads them at gl/glquake.h:38
# before the config file is #include'd from common/bothdefs.h:101. FTE_GLES is
# the trigger the local patch adds to that #if, so the build takes the GLES2
# headers (the ANDROID branch already set that path up) instead of the desktop
# GL ones. -DANDROID itself is not usable here: it drags Android's own code
# paths in, and only the header branch is wanted.
#
# FTE_TARGET=SDL is the plain SDL target. SDL2 would be the tempting name, and
# it is a trap: line 727 matches it exactly and replaces the dynamic link lines
# with libs-$(ARCH)/libpng.a libvorbisfile.a ... static archives that only
# `make makelibs` builds. FTE_TARGET=SDL keeps the shared-library links against
# the sysroot instead, and SDLCONFIG is what picks the SDL major version, so it
# is pointed at the SDL2 answers below. BITS stays unset, which keeps FTE_FULLTARGET
# simply "sdl"; the output binary then comes out of engine/release/ as
# fteqw-glsdl (GL_EXE_NAME at line 1145 with EXE_NAME=FTE_CONFIG).
#
# gl-rel, not glcl-rel, is the target this recipe runs. Building.md lists both as
# GL renders (:133 and :135), but the client-only variant cannot build against
# this tree: glcl-tmp adds -DCLIENTONLY through CLIENT_ONLY_CFLAGS (engine/Makefile:665),
# and that is what makes engine/client/quakedef.h:412 take the "#define isDedicated
# false" branch instead of the "extern qboolean isDedicated" one at :414. engine/
# client/sys_sdl.c:1468 then reads "false = true;" and gcc stops it with
#   sys_sdl.c:1468:29: error: lvalue required as left operand of assignment
# (the SV_Init/SV_Frame calls in that same function would also have nothing to
# link against, since the client-only object set leaves SERVER_OBJS out). gl-rel
# runs _clsv-rel, whose JOINT_CFLAGS at engine/Makefile:667 is empty, so both
# HAVE_CLIENT and HAVE_SERVER come out of common/bothdefs.h:146 and isDedicated
# is the real variable sys_sdl.c:29 defines - which is also what the seat can
# use: the same binary plays, and can run -dedicated if wanted.
#
# PKGCONFIG is left at the makefile's own cross-compiling answer: line 709 looks
# for $(ARCH)-pkg-config on PATH, finds nothing, and sets it to /bin/false so no
# host /usr/include leaks in. The cost of that answer is that the link lines it
# would have produced come out empty, so the ones that matter are passed here
# explicitly instead: IMAGELDFLAGS (png/jpeg/zlib), OGGVORBISLDFLAGS (the
# soundtrack) and FREETYPE_LDFLAGS (engine/Makefile:1058 takes it from pkg-config,
# and the :1054 fallback that would give -lfreetype is not the branch taken).
# The sysroot already carries libpng.so, libjpeg.so, libfreetype.so, libogg.so,
# libvorbis.so and libvorbisfile.so, and the default search directory comes from
# CC's --sysroot, so plain -l names are what the linker needs.
do_compile:prepend() {
    # The upstream edit must be where the patch says it is. bitbake applies SRC_URI
    # patches before this runs, and a hunk that silently missed would otherwise
    # build a binary that is quietly not the one the README describes.
    if ! grep -q 'FTE_GLES' "${S}/engine/gl/glquake.h"; then
        bbfatal "fteqw: engine/gl/glquake.h does not carry the FTE_GLES branch that 0001-use-the-gles2-headers-instead-of-the-desktop-gl-ones.patch adds; the patch did not apply to this pin."
    fi

    # The makefile asks SDLCONFIG two questions: --cflags and --libs. There is
    # no sdl2-config in this sysroot (the SDL2 recipes ship the library and its
    # headers, not the vendor's config script), and the cross pkg-config is not
    # what the makefile would find (PKGCONFIG above is /bin/false), so answer
    # the two questions directly. The --cflags answer is not empty after all:
    # FTEQW includes <SDL.h> while SDL2's own headers live one directory deeper
    # in this sysroot (${WORKDIR}/recipe-sysroot/usr/include/SDL2/SDL.h, which
    # is what the vendor's own script returns as -I/usr/include/SDL2), so the
    # include dir has to be handed to the compiler explicitly.
    printf '%s\n' '#!/bin/sh' 'case "$1" in' '    --cflags ) echo "-I${WORKDIR}/recipe-sysroot/usr/include/SDL2" ;;' '    --libs | --static-libs ) echo "-lSDL2" ;;' 'esac' > "${WORKDIR}/fteqw-sdl2-config"
    chmod 0755 "${WORKDIR}/fteqw-sdl2-config"
}

do_compile() {
    make -C "${S}/engine" gl-rel \
        FTE_CONFIG=fteqw \
        FTE_TARGET=SDL \
        SDLCONFIG="${WORKDIR}/fteqw-sdl2-config" \
        PKGCONFIG=/bin/false \
        IMAGELDFLAGS="-lpng -ljpeg -lz" \
        OGGVORBISLDFLAGS="-lvorbisfile -lvorbis -logg" \
        FREETYPE_LDFLAGS="-lfreetype" \
        STRIP="${STRIP}" \
        CFLAGS="${CFLAGS} -DGLESONLY -DGLSLONLY -DFTE_GLES"
}

# STRIP is handed over as OE's own cross strip, which is what makes the makefile
# take the _out-rel branch at engine/Makefile:2077 (link to $(EXE_NAME).db, then
# $(STRIP) $(STRIPFLAGS) ... -o $(EXE_NAME); GNU strip does accept -o). Leaving
# STRIP at the makefile's default "strip" would run the host's, which cannot read
# an aarch64 object. The link and the strip both happen against release/fteqw_sdl/
# and GL_EXE_NAME at line 1145 is ../$(EXE_NAME)-gl$(FTE_FULLTARGET), one directory
# above that, so the binary ends up at engine/release/fteqw-glsdl.
#
# It is installed under a name that says what it is, and /usr/bin/fteqw is the
# launcher the seat actually runs: the game data is never beside the executable,
# it is the payload the data recipes install under /usr/share, and FTEQW reads
# one game only, the one -basedir and -game name. The default is LibreQuake,
# whose tree is id1 under /usr/share/librequake, so the pair is
# -basedir /usr/share/librequake -game id1; -quake then states the protocol,
# because the directory name alone does not say which game the tree is.
#
# The three sets in FTEQW_ARGS are the two facts this board needs at startup and
# one the LibreQuake progs set needs:
#   - vid_gl_context_es 1 asks the Vivante driver for a GL ES 3.1 context. Left
#     alone it hands back an ES-CM 1.1 one and the renderer cannot draw at all.
#   - vid_fullscreen 2 keeps the window on the seat's own desktop size.
#   - pr_ssqc_memsize 134217728 is the Quake-C VM arena: the engine mallocs it
#     whole and re-mallocs it as the progs grow, and LibreQuake ships progs.dat,
#     whose sprite sequence needs 128mb where the default is 32mb.
do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/engine/release/fteqw-glsdl ${D}${bindir}/fteqw-gles

    printf '%s\n' \
        '#!/bin/sh' \
        '# Launcher for the FTEQW gles client this layer builds.' \
        '# The default game is the LibreQuake payload this layer installs at' \
        '# /usr/share/librequake/id1, so the pair is -basedir /usr/share/librequake' \
        '# -game id1 and -quake says which protocol that tree speaks. OpenArena' \
        '# speaks a different protocol and has its own launcher, /usr/bin/openarena.' \
        '# Override with FTEQW_BASEDIR / FTEQW_GAME / FTEQW_ARGS, or pass your own' \
        '# -basedir, -game and -set arguments on the command line, which win.' \
        'basedir=${FTEQW_BASEDIR:-/usr/share/librequake}' \
        'game=${FTEQW_GAME:-id1}' \
        'fteargs=${FTEQW_ARGS:--set vid_gl_context_es 1 -set vid_fullscreen 2 -set pr_ssqc_memsize 134217728}' \
        'exec /usr/bin/fteqw-gles -basedir "$basedir" -game "$game" -quake -updatesrc "" $fteargs "$@"' \
        > ${D}${bindir}/fteqw
    chmod 0755 ${D}${bindir}/fteqw
}

# The makefile strips its own release binary: the _out-rel rule at
# engine/Makefile:2077 links the executable as $(EXE_NAME).db and then runs
# $(STRIP) $(STRIPFLAGS) --strip-unneeded ... -o $(EXE_NAME) over it to get
# the very name installed above, so by the time do_install sees the file
# there is no debug info left for OE to strip and its already-stripped QA
# check is the expected outcome of that build, not a mistake - the .db FTE
# leaves beside it in engine/release/ is what carries the symbols. Skipping
# just this one check is what the other pre-stripped recipes in this layer
# do (opennow, opennowlite).
INSANE_SKIP:${PN} += "already-stripped"
