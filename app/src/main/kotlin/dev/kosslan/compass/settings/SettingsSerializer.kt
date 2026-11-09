package dev.kosslan.compass.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/** Stores the settings encrypted as a whole, since they hold the node key and auth keys. */
class SettingsSerializer(
    private val cipher: SecretCipher,
) : Serializer<CompassSettings> {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val defaultValue = CompassSettings()

    override suspend fun readFrom(input: InputStream): CompassSettings {
        val sealed = input.readBytes()

        if (sealed.isEmpty()) {
            return defaultValue
        }

        return try {
            json.decodeFromString(
                CompassSettings.serializer(),
                cipher.decrypt(sealed).decodeToString(),
            )
        } catch (error: AEADBadTagException) {
            throw CorruptionException("the settings cannot be decrypted", error)
        } catch (error: GeneralSecurityException) {
            throw IOException("the keystore failed to decrypt the settings", error)
        } catch (error: SerializationException) {
            throw CorruptionException("the settings cannot be read", error)
        }
    }

    override suspend fun writeTo(
        t: CompassSettings,
        output: OutputStream,
    ) {
        output.write(
            cipher.encrypt(
                json.encodeToString(CompassSettings.serializer(), t).encodeToByteArray(),
            ),
        )
    }
}
