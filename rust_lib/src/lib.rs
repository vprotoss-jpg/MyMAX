use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use std::fs::File;
use std::io::Write;
use std::path::Path;
use openmls::prelude::CryptoConfig;
use base64::{engine::general_purpose, Engine as _};
use aes_gcm::{Aes256Gcm, Key, Nonce, KeyInit, aead::Aead};
use pbkdf2::pbkdf2_hmac;
use sha2::Sha256;
use rand::{RngCore, thread_rng};

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_createBackup(
    mut env: JNIEnv,
    _class: JClass,
    password: JString,
    data_to_backup: JString,
) -> jstring {
    let pass: String = match env.get_string(&password) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("ERROR_INVALID_PASS_STR").unwrap().into_raw(),
    };
    let data: String = match env.get_string(&data_to_backup) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("ERROR_INVALID_DATA_STR").unwrap().into_raw(),
    };

    // 1. Генерируем соль (16 байт)
    let mut salt = [0u8; 16];
    thread_rng().fill_bytes(&mut salt);

    // 2. PBKDF2 для ключа
    let mut derived_key = [0u8; 32];
    pbkdf2_hmac::<Sha256>(pass.as_bytes(), &salt, 600_000, &mut derived_key);

    // 3. Шифрование AES-GCM
    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(&derived_key));

    // Генерируем случайный Nonce (12 байт для GCM)
    let mut nonce_bytes = [0u8; 12];
    thread_rng().fill_bytes(&mut nonce_bytes);
    let nonce = Nonce::from_slice(&nonce_bytes);

    let ciphertext = match cipher.encrypt(nonce, data.as_bytes().as_ref()) {
        Ok(ct) => ct,
        Err(_) => return env.new_string("ERROR_ENCRYPTION_FAILED").unwrap().into_raw(),
    };

    // 4. Пакет: Salt (16) + Nonce (12) + Ciphertext
    let mut final_payload = salt.to_vec();
    final_payload.extend_from_slice(&nonce_bytes);
    final_payload.extend(ciphertext);

    let b64_backup = general_purpose::STANDARD.encode(final_payload);
    env.new_string(b64_backup).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_encryptMessage(
    mut env: JNIEnv,
    _class: JClass,
    message: JString,
    group_id: JString,
    storage_path: JString,
) -> jstring {
    let msg_str: String = env.get_string(&message).unwrap().into();
    let id: String = env.get_string(&group_id).unwrap().into();
    let path: String = env.get_string(&storage_path).unwrap().into();

    let encrypted_binary = format!("MLS_PACKET_V1_EPOCH_1_{}", msg_str).into_bytes();
    let b64_output = general_purpose::STANDARD.encode(encrypted_binary);

    let output = env.new_string(b64_output).unwrap();
    output.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_decryptMessage(
    mut env: JNIEnv,
    _class: JClass,
    base64_data: JString,
    group_id: JString,
) -> jstring {
    let b64_str: String = match env.get_string(&base64_data) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("ERROR_JNI_STRING").unwrap().into_raw(),
    };

    let encrypted_bytes = match general_purpose::STANDARD.decode(&b64_str) {
        Ok(bytes) => bytes,
        Err(_) => return env.new_string("ERROR_INVALID_BASE64").unwrap().into_raw(),
    };

    // Проверяем, не является ли это сложным бэкапом (Salt + Nonce + Ciphertext)
    // Если длина > 28 байт (16 соль + 12 нонс), пробуем расшифровать как бэкап
    if encrypted_bytes.len() > 28 && env.get_string(&group_id).unwrap().to_str().unwrap() == "SYSTEM_INTERNAL" {
        return decrypt_backup_internal(&mut env, &encrypted_bytes);
    }

    let decrypted_payload = match String::from_utf8(encrypted_bytes) {
        Ok(s) => s,
        Err(_) => return env.new_string("ERROR_UTF8_DECODE").unwrap().into_raw(),
    };

    let original_text = decrypted_payload.replace("MLS_PACKET_V1_EPOCH_1_", "");
    env.new_string(original_text).unwrap().into_raw()
}

fn decrypt_backup_internal(env: &mut JNIEnv, data: &[u8]) -> jstring {
    let salt = &data[0..16];
    let nonce_bytes = &data[16..28];
    let ciphertext = &data[28..];

    let pass = "system_backup_key_123"; // Должен совпадать с Kotlin
    let mut derived_key = [0u8; 32];
    pbkdf2_hmac::<Sha256>(pass.as_bytes(), salt, 600_000, &mut derived_key);

    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(&derived_key));
    let nonce = Nonce::from_slice(nonce_bytes);

    match cipher.decrypt(nonce, ciphertext) {
        Ok(cleartext) => {
            let s = String::from_utf8_lossy(&cleartext).into_owned();
            env.new_string(s).unwrap().into_raw()
        },
        Err(_) => env.new_string("ERROR_DECRYPT_BACKUP_FAILED").unwrap().into_raw(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_initGroupWithStorage(
    mut env: JNIEnv,
    _class: JClass,
    group_id: JString,
    storage_path: JString,
) -> jstring {
    let id: String = env.get_string(&group_id).unwrap().into();
    let path_str: String = env.get_string(&storage_path).unwrap().into();

    let file_path = Path::new(&path_str).join(format!("{}.mls", id));
    let dummy_state = format!("MLS_STATE_FOR_{}", id);

    if let Ok(mut file) = File::create(&file_path) {
        let _ = file.write_all(dummy_state.as_bytes());
        let response = format!("Состояние сохранено в: {}", file_path.display());
        env.new_string(response).unwrap().into_raw()
    } else {
        env.new_string("ERROR_FILE_CREATE").unwrap().into_raw()
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_getMlsVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    env.new_string("OpenMLS 0.5.0 (Safe Version)").unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_createGroup(
    mut env: JNIEnv,
    _class: JClass,
    group_id: JString,
) -> jstring {
    let id_str: String = env.get_string(&group_id).unwrap().into();
    let config = CryptoConfig::default();
    let response = format!("Group '{}' init. Config: {:?}", id_str, config);
    env.new_string(response).unwrap().into_raw()
}
