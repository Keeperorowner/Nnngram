//
// Created by qwq233 on 2/24/2023.
//
#include <jni.h>
#include <regex>
#include <cstring>
#include <string>
#include <string_view>

#include <unistd.h>
#include <errno.h>
#include <dirent.h>

#include <android/log.h>
#include "../crashlytics.h"
#include "../log.h"

namespace {
extern "C" bool checkSignature(uint8_t result) {
    bool match = false;
    if (result == 1) {
        firebase::crashlytics::SetCustomKey("signature", "github");
        match = true;
    } else if (result == 2) {
        firebase::crashlytics::SetCustomKey("signature", "play");
        match = true;
    } else {
        firebase::crashlytics::SetCustomKey("signature", "verify failed");
        match = false;
    }
    return match;
}
}

#undef __STRING
#undef STRING
