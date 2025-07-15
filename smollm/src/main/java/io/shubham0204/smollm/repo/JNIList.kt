package io.shubham0204.smollm.repo


val jniLibs = listOf(
    //arm64-v8a libs
    JNILIB(
        "libggufreader",
        "https://drive.google.com/uc?export=download&id=1_yrUPQyqhIsKhSQfD9Vb4skjthCaW1xu",
        Architecture.ARM64
    ),
    JNILIB(
        "libsmollm",
        "https://drive.google.com/uc?export=download&id=1993hXv6CV_8_fQljriXFuF5mvpeiU-Yt",
        Architecture.ARM64
    ),
    JNILIB(
        "libsmollm_v8",
        "https://drive.google.com/uc?export=download&id=1G7oLJIZYd34HR4Ql90R-tM2h8cAWJez9",
        Architecture.ARM64
    ),

    JNILIB(
        "libsmollm_v8_2_fp16",
        "https://drive.google.com/uc?export=download&id=1EMawRVPwBef8_j_06SiLjp0ivxUELEKD",
        Architecture.ARM64
    ),
    JNILIB(
        "libsmollm_v8_2_fp16_dotprod",
        "https://drive.google.com/uc?export=download&id=1aSlbC6-MWK5UFvNxqzokzj3gqUyTjiGe",
        Architecture.ARM64
    ),
    JNILIB(
        "libsmollm_v8_4_fp16_dotprod",
        "https://drive.google.com/uc?export=download&id=1voTTjSU3Aq_mEBW_uECAlx5TSYgTa58_",
        Architecture.ARM64
    ),

    JNILIB(
        "libsmollm_v8_4_fp16_dotprod_i8mm",
        "https://drive.google.com/uc?export=download&id=1mwTWXpE7Qw5Rr4zaDpd5sJGRCt26vC7q",
        Architecture.ARM64
    ),
    JNILIB(
        "libsmollm_v8_4_fp16_dotprod_i8mm_sve",
        "https://drive.google.com/uc?export=download&id=1E9O6Orw9pYWViIKcCLrTUzJHeh1pYEgY",
        Architecture.ARM64
    ),
    JNILIB(
        "libsmollm_v8_4_fp16_dotprod_sve",
        "https://drive.google.com/uc?export=download&id=15Odu951sqb5E9KCH4_4Q4c6bZpKrQb_c",
        Architecture.ARM64
    ),


    //armeabi-v7a libs
    JNILIB(
        "libggufreader",
        "https://drive.google.com/uc?export=download&id=1lxiUft0lPv8bunIShK0aTyucfAqxQtUk",
        Architecture.ARM
    ),
    JNILIB(
        "libsmollm",
        "https://drive.google.com/uc?export=download&id=1lshlyYby1ceyINwkqYpCQQHLda_KYoFX",
        Architecture.ARM
    ),
    JNILIB(
        "libsmollm_v7a",
        "https://drive.google.com/uc?export=download&id=16gzt4H0nBpMAWixzcC0s3pOQVWPjRiyJ",
        Architecture.ARM
    ),


)


data class JNILIB(val name: String, val link: String, val architecture: Architecture)

enum class Architecture {
    X86, X86_64, ARM, ARM64
}