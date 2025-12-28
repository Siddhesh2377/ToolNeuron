
//
// cpu_helper.h
// Lightweight runtime helpers for Android CPUs
//

#pragma once
#ifdef __cplusplus
extern "C" {
#endif

/**
 * Return the number of *physical* cores on the device.
 * (Counts unique core_id entries under /sys/devices/system/cpu/…)
 *
 * Falls back to android_getCpuCount() if /sys topology is absent.
 */
int count_physical_cores(void);

#ifdef __cplusplus
}   // extern "C"
#endif