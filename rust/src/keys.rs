use compass_proto::{DnsName, PrivateKey, PublicKey};

use crate::error::CompassError;

#[uniffi::export]
pub fn generate_private_key() -> String {
    PrivateKey::generate().to_base64().to_string()
}

#[uniffi::export]
pub fn public_key(private_key: String) -> Result<String, CompassError> {
    PrivateKey::from_base64(&private_key)
        .map(|key| key.public_key().to_string())
        .map_err(CompassError::invalid)
}

#[uniffi::export]
pub fn is_public_key(key: String) -> bool {
    key.parse::<PublicKey>().is_ok()
}

/// Turns a free-form device name into a hostname the lighthouse accepts.
#[uniffi::export]
pub fn sanitize_hostname(name: String) -> Option<String> {
    DnsName::sanitize_label(&name).map(|label| label.to_string())
}
