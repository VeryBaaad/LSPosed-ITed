/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 - 2022 LSPosed Contributors
 */

#include <sys/socket.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/mman.h>

#include <filesystem>
#include <fstream>
#include <iterator>

#include "machikado.h"
#include "zygisk.h"
#include "logging.h"
#include "loader.h"
#include "config_impl.h"
#include "magisk_loader.h"
#include "symbol_cache.h"

namespace lspd {
    int allow_unload = 0;
    int *allowUnload = &allow_unload;

    static const std::filesystem::path kModuleDir = "/data/adb/modules/zygisk_lsposed";
    static const std::string kModuleId = "zygisk_lsposed";
    static const machikado::PublicKey kExpectedOrgPk = {
        0x5C, 0x77, 0xD4, 0x10, 0xD3, 0xCC, 0x41, 0xEF,
        0x5F, 0xC6, 0x17, 0x25, 0xB2, 0xB2, 0xDB, 0x82,
        0xC1, 0xC9, 0x0F, 0xA5, 0xC0, 0xE9, 0x06, 0xD8,
        0x80, 0x06, 0xF9, 0x32, 0x0D, 0x43, 0x7B, 0x1A,
    };

    static bool ValidateMachikadoModule() {
        const auto machikado_path = kModuleDir / "machikado";
        const auto mazoku_path = kModuleDir / "mazoku";

        if (!std::filesystem::exists(machikado_path) || !std::filesystem::exists(mazoku_path)) {
            LOGE("module safety verification failed: missing machikado/mazoku files under %s",
                 kModuleDir.c_str());
            return false;
        }

        std::ifstream machikado_in(machikado_path, std::ios::binary);
        std::vector<std::uint8_t> machikado_blob(
                (std::istreambuf_iterator<char>(machikado_in)),
                std::istreambuf_iterator<char>());

        std::ifstream mazoku_in(mazoku_path, std::ios::binary);
        std::vector<std::uint8_t> mazoku_blob(
                (std::istreambuf_iterator<char>(mazoku_in)),
                std::istreambuf_iterator<char>());

        auto entries_opt = machikado::load_folder_files(kModuleDir, {}, {"machikado", "system.prop"}, nullptr);
        if (!entries_opt) {
            LOGE("module safety verification failed: cannot load");
            return false;
        }

        auto entries = *entries_opt;
        auto [ok, err] = machikado::verify(machikado_blob, mazoku_blob, entries, kModuleId, kExpectedOrgPk);

        if (!ok) {
            if (err) {
                LOGE("module safety verification failed: %s", machikado::to_string(err.value()));
            } else {
                LOGE("module safety verification failed");
            }
            return false;
        }
        return true;
    }

    class ZygiskModule : public zygisk::ModuleBase {
        JNIEnv *env_;
        zygisk::Api *api_;
        bool validated_ = true;

        void onLoad(zygisk::Api *api, JNIEnv *env) override {
            env_ = env;
            api_ = api;
            validated_ = ValidateMachikadoModule();
            MagiskLoader::Init();
            ConfigImpl::Init();
        }

        void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
            if (!validated_) {
                api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
                return;
            }

            MagiskLoader::GetInstance()->OnNativeForkAndSpecializePre(
                    env_, args->uid, args->gids, args->nice_name,
                    args->is_child_zygote ? *args->is_child_zygote : false, args->app_data_dir);
        }

        void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
            if (!validated_) {
                api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
                return;
            }
            MagiskLoader::GetInstance()->OnNativeForkAndSpecializePost(env_, args->nice_name, args->app_data_dir);
            if (*allowUnload) api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        }

        void preServerSpecialize([[maybe_unused]] zygisk::ServerSpecializeArgs *args) override {
            if (!validated_) {
                api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
                return;
            }

            MagiskLoader::GetInstance()->OnNativeForkSystemServerPre(env_);
        }

        void postServerSpecialize([[maybe_unused]] const zygisk::ServerSpecializeArgs *args) override {
            if (!validated_) {
                api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
                return;
            }
            if (__system_property_find("ro.vendor.product.ztename")) {
                auto *process = env_->FindClass("android/os/Process");
                auto *set_argv0 = env_->GetStaticMethodID(process, "setArgV0",
                                                          "(Ljava/lang/String;)V");
                auto *name = env_->NewStringUTF("system_server");
                env_->CallStaticVoidMethod(process, set_argv0, name);
                env_->DeleteLocalRef(name);
                env_->DeleteLocalRef(process);
            }
            MagiskLoader::GetInstance()->OnNativeForkSystemServerPost(env_);
            if (*allowUnload) api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        }
    };
} //namespace lspd

REGISTER_ZYGISK_MODULE(lspd::ZygiskModule);
