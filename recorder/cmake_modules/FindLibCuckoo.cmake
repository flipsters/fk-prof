find_path(LIBCUCKOO_ROOT include/libcuckoo/cuckoohash_map.hh)
find_library(CITY_HASH_LIBRARY cityhash)

if(LIBCUCKOO_ROOT)
    set(LIBCUCKOO_FOUND TRUE)
    set(LIBCUCKOO_INCLUDE_DIRS "${LIBCUCKOO_ROOT}/include/libcuckoo")
    set(LIBCUCKOO_CITY_HASH_LIB "cityhash")
else()
    message("Can't find libcuckoo!")
    if(LibCuckoo_FIND_REQUIRED)
        message(FATAL_ERROR "Missing required package libcuckoo")
    endif()
endif()
