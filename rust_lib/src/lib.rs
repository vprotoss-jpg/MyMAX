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
    let pass: String = env.get_string(&password).unwrap().into();
    let data: String = env.get_string(&data_to_backup).unwrap().into();

    // 1. Генерируем соль (чтобы нельзя было подобрать по словарю)
    let mut salt = [0u8; 16];
    thread_rng().fill_bytes(&mut salt);

    // 2. Генерируем 256-битный ключ из пароля (600 000 итераций)
    let mut derived_key = [0u8; 32];
    pbkdf2_hmac::<Sha256>(pass.as_bytes(), &salt, 600_000, &mut derived_key);

    // 3. Шифруем данные с помощью AES-GCM
    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(&derived_key));
    let nonce = Nonce::from_slice(b"unique nonce!!"); // В идеале тоже рандомный

    let ciphertext = cipher.encrypt(nonce, data.as_bytes().as_ref())
        .expect("Ошибка шифрования бэкапа");

    // 4. Склеиваем Соль + Шифртекст и превращаем в Base64
    let mut final_payload = salt.to_vec();
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

    // 1. В реальном MLS мы бы загрузили GroupState из файла:
    // let group_state = GroupState::load(path, id);

    // 2. Имитируем "зашифрованный" бинарный пакет OpenMLS
    // Настоящий MLS пакет включает в себя: Epoch, Content, Signature
    let encrypted_binary = format!("MLS_PACKET_V1_EPOCH_1_{}", msg_str).into_bytes();

    // 3. Кодируем в Base64 для безопасной отправки через JSON/Max API
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
    // Используем макрос для безопасного извлечения, чтобы не было паники
    let b64_str: String = match env.get_string(&base64_data) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("ERROR_JNI_STRING").unwrap().into_raw(),
    };

    // Безопасное декодирование Base64 вместо expect()
    let encrypted_bytes = match general_purpose::STANDARD.decode(&b64_str) {
        Ok(bytes) => bytes,
        Err(_) => return env.new_string("ERROR_INVALID_BASE64").unwrap().into_raw(),
    };

    let decrypted_payload = match String::from_utf8(encrypted_bytes) {
        Ok(s) => s,
        Err(_) => return env.new_string("ERROR_UTF8_DECODE").unwrap().into_raw(),
    };

    let original_text = decrypted_payload.replace("MLS_PACKET_V1_EPOCH_1_", "");
    env.new_string(original_text).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_initGroupWithStorage(
    mut env: JNIEnv,
    _class: JClass,
    group_id: JString,
    storage_path: JString,
) -> jstring {
    // 1. Извлекаем строки из Java
    let id: String = env.get_string(&group_id).unwrap().into();
    let path_str: String = env.get_string(&storage_path).unwrap().into();

    // 2. Формируем путь к файлу (например, group_001.mls)
    let file_path = Path::new(&path_str).join(format!("{}.mls", id));

    // 3. Имитируем сохранение состояния OpenMLS в файл
    // В реальности здесь будет serialized_group.encode()
    let dummy_state = format!("MLS_STATE_FOR_{}", id);
    let mut file = File::create(&file_path).expect("Не удалось создать файл");
    file.write_all(dummy_state.as_bytes()).expect("Ошибка записи");

    let response = format!("Состояние сохранено в: {}", file_path.display());

    let output = env.new_string(response).unwrap();
    output.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_getMlsVersion(
    env: JNIEnv, // Убрали mut, так как он тут не нужен
    _class: JClass,
) -> jstring {
    let version_info = "OpenMLS 0.5.0 initialized via Rust";

    // Создаем Java-строку и возвращаем её
    let output = env.new_string(version_info).unwrap();
    output.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ru_mglife_mymax_MLSManager_createGroup(
    mut env: JNIEnv, // Добавили mut, чтобы env можно было изменять
    _class: JClass,
    group_id: JString,
) -> jstring {
    // 1. Читаем строку из Java
    let id_str: String = env.get_string(&group_id)
        .expect("Couldn't get java string!")
        .into();

    // 2. Используем OpenMLS для создания конфигурации (чтобы проверить импорт)
    // Ciphersuite 1792 — это стандартный набор (Curve25519, AES-GCM, SHA256)
    let config = CryptoConfig::default();

    // 3. Формируем ответ
    let response = format!(
        "Группа '{}' инициализирована. MLS Config: {:?}",
        id_str, config
    );

    let output = env.new_string(response).unwrap();
    output.into_raw()
}
