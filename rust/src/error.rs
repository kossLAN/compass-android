use std::fmt::Display;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CompassError {
    #[error("{reason}")]
    InvalidSettings { reason: String },
    #[error("the client has stopped")]
    Stopped,
}

impl CompassError {
    pub fn invalid(error: impl Display) -> Self {
        Self::InvalidSettings {
            reason: error.to_string(),
        }
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum TunError {
    #[error("{reason}")]
    Failed { reason: String },
}

impl From<uniffi::UnexpectedUniFFICallbackError> for TunError {
    fn from(error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        Self::Failed {
            reason: error.reason,
        }
    }
}
