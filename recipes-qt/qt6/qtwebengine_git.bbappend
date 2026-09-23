# QtWebEngine launches its inner chromium ninja (cmake/Functions.cmake)
# WITHOUT -j, so it defaults to one job per CPU -> 8-11 concurrent cc1plus,
# each using 0.5-2 GB of RAM. On an 8 GB host that means constant swap
# thrashing and a near-frozen machine.
# Ninja parallelism is controlled centrally via PARALLEL_MAKE in the build
# conf/local.conf (inherited as NINJAFLAGS), so no -j is set here.