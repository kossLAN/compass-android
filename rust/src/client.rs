use std::sync::{Arc, LazyLock};

use compass_core::client::ReloadError;
use compass_core::{Client, HostedPlatform};
use tokio::runtime::Runtime;
use tokio::sync::{RwLock, oneshot};

use crate::error::CompassError;
use crate::settings::{ClientSettings, UnderlyingNetwork};
use crate::status::StatusReport;
use crate::tun::{ForeignTun, TunProvider};

static RUNTIME: LazyLock<Runtime> = LazyLock::new(|| {
    tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .thread_name("compass")
        .build()
        .expect("the tokio runtime starts")
});

#[derive(uniffi::Object)]
pub struct CompassClient {
    client: RwLock<Option<Client<HostedPlatform>>>,
    platform: HostedPlatform,
}

#[uniffi::export]
impl CompassClient {
    #[uniffi::constructor]
    pub async fn start(
        settings: ClientSettings,
        network: UnderlyingNetwork,
        tun_provider: Arc<dyn TunProvider>,
    ) -> Result<Arc<Self>, CompassError> {
        let settings = settings.try_into()?;
        let platform = HostedPlatform::new(Arc::new(ForeignTun(tun_provider)));
        platform.set_underlying(network.into());

        let (started, client) = oneshot::channel();
        let starting = platform.clone();
        RUNTIME.spawn(async move {
            let result = Client::start(settings, (), starting).await;

            // The app gave up waiting, so nothing else will ever stop this client.
            if let Err(Ok(client)) = started.send(result) {
                client.shutdown().await;
            }
        });
        let client = client
            .await
            .map_err(|_| CompassError::Stopped)?
            .map_err(CompassError::invalid)?;

        Ok(Arc::new(Self {
            client: RwLock::new(Some(client)),
            platform,
        }))
    }

    pub async fn status(self: Arc<Self>) -> Result<StatusReport, CompassError> {
        RUNTIME
            .spawn(async move {
                let client = self.client.read().await;
                let client = client.as_ref().ok_or(CompassError::Stopped)?;
                client.status().await.map_err(|_| CompassError::Stopped)
            })
            .await
            .map_err(|_| CompassError::Stopped)?
            .map(Into::into)
    }

    pub async fn reload(self: Arc<Self>, settings: ClientSettings) -> Result<(), CompassError> {
        let settings = settings.try_into()?;
        RUNTIME
            .spawn(async move {
                let client = self.client.read().await;
                let client = client.as_ref().ok_or(CompassError::Stopped)?;
                client
                    .reload(settings, ())
                    .await
                    .map_err(|error| match error {
                        ReloadError::Config(error) => CompassError::invalid(error),
                        ReloadError::Stopped(_) => CompassError::Stopped,
                    })
            })
            .await
            .map_err(|_| CompassError::Stopped)?
    }

    pub fn set_underlying_network(&self, network: UnderlyingNetwork) {
        self.platform.set_underlying(network.into());
    }

    pub async fn shutdown(self: Arc<Self>) {
        let stopped = RUNTIME.spawn(async move {
            let client = self.client.write().await.take();

            if let Some(client) = client {
                client.shutdown().await;
            }
        });

        if let Err(error) = stopped.await {
            tracing::error!(%error, "the client did not shut down cleanly");
        }
    }
}
