# Machine-neutral job cap for the nested QtWebEngineCore build.
#
# qtwebengine's generated top-level build.ninja contains a single nested
# command (a COMMAND = line) of the form:
#     ... ninja -C <release-dir> QtWebEngineCore
# with no job cap. Ninja then defaults to all CPUs and OOM-kills cc1plus on
# the V8 jumbo objects on a 15 GiB build box. This bbappend inserts a job
# cap into that single nested command, driven by the WEBENGINE_CORE_JOBS knob
# (e.g. WEBENGINE_CORE_JOBS = "-j2" in conf/local.conf). It is a no-op when
# the knob is unset, so it is machine-neutral and pushable without touching
# meta-qt6.
#
# The sed is idempotent: it only matches the nested command while it still
# lacks a job cap, so re-running it on an already-fixed build.ninja is a
# harmless no-op.
do_compile:prepend() {
    if [ -n "${WEBENGINE_CORE_JOBS}" ]; then
        sed -i -E "s|(ninja -C [^ ]+)[[:space:]]+QtWebEngineCore\$|\1 ${WEBENGINE_CORE_JOBS} QtWebEngineCore|" ${B}/build.ninja
    fi
}
