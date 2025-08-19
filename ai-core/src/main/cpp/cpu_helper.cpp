#include "cpu_helper.h"

#include <dirent.h>
#include <cctype>
#include <cstdio>
#include <set>
#include <string>
#include <cpu-features.h>

// ---------------------------------------------------------------------------
// Count unique values in /sys/devices/system/cpu/cpu*/topology/core_id
// ---------------------------------------------------------------------------
int count_physical_cores(void) {
    std::set<int> coreIds;

    DIR *dir = opendir("/sys/devices/system/cpu");
    if (!dir) {
        return android_getCpuCount();   // very old kernels or permission issues
    }

    struct dirent *dent;
    while ((dent = readdir(dir)) != nullptr) {
        // match "cpu0", "cpu11", …
        if (strncmp(dent->d_name, "cpu", 3) != 0 || !std::isdigit(dent->d_name[3]))
            continue;

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

    return coreIds.empty() ? android_getCpuCount()                 // fallback
                           : static_cast<int>(coreIds.size());     // physical core count
}
