// src/models/cpu_helper.cpp
#include "cpu_helper.h"

#if defined(__ANDROID__)
#include "cpu-features.h"
#include <dirent.h>
#include <cctype>
#include <cstdio>
#include <set>
#include <string>
#include <sys/types.h>

int count_physical_cores(void) {
    std::set<int> coreIds;

    DIR *dir = opendir("/sys/devices/system/cpu");
    if (!dir) {
        return android_getCpuCount();   // old kernels / no topology
    }

    struct dirent *dent;
    while ((dent = readdir(dir)) != nullptr) {
        if (strncmp(dent->d_name, "cpu", 3) != 0 || !std::isdigit(dent->d_name[3]))
            continue;          // ignore non‑cpu directories

        std::string path = "/sys/devices/system/cpu/";
        path += dent->d_name;
        path += "/topology/core_id";

        FILE *f = fopen(path.c_str(), "r");
        if (!f) continue;

        int id = -1;
        if (fscanf(f, "%d", &id) == 1 && id >= 0)
            coreIds.insert(id);
        fclose(f);
    }
    closedir(dir);

    return coreIds.empty()
           ? android_getCpuCount()
           : static_cast<int>(coreIds.size());
}
#else
// ─────── non‑Android fallback ───────
// (used when building on host, e.g. in CI or tests)
int count_physical_cores(void) { return 1; }
#endif