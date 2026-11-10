package dev.oss.adbcontrol

import java.math.BigInteger
import java.security.Key
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec

/**
 * Android 9 (and some vendor ROMs) parse PKCS#8 RSA keys into keys that do not
 * implement [java.security.interfaces.RSAPrivateCrtKey]. Kadb needs the CRT
 * parameters to derive the ADB public key, so connecting fails with
 * "Only RSA private keys with CRT parameters are supported".
 *
 * This provider intercepts `KeyFactory.RSA` and rebuilds the private key from
 * its CRT parameters, which makes the platform return a CRT-capable key that
 * can also be used for signing.
 */
object RsaCrtFix {
    private const val PROVIDER_NAME = "AdbRsaCrt"

    fun install() {
        if (Security.getProvider(PROVIDER_NAME) == null) {
            runCatching { Security.insertProviderAt(RsaCrtProvider(), 1) }
        }
    }

    class RsaCrtProvider : Provider(PROVIDER_NAME, 1.0, "ADB RSA CRT key factory") {
        init {
            put("KeyFactory.RSA", RsaKeyFactory::class.java.name)
        }
    }

    class RsaKeyFactory : java.security.KeyFactorySpi() {
        override fun engineGeneratePrivate(keySpec: KeySpec): PrivateKey {
            if (keySpec is PKCS8EncodedKeySpec) {
                val spec = runCatching { crtSpec(keySpec.encoded) }.getOrNull()
                if (spec != null) {
                    runCatching { return platform().generatePrivate(spec) }
                }
            }
            return platform().generatePrivate(keySpec)
        }

        override fun engineGeneratePublic(keySpec: KeySpec): PublicKey =
            platform().generatePublic(keySpec)

        override fun <T : KeySpec> engineGetKeySpec(key: Key, keySpec: Class<T>): T =
            platform().getKeySpec(key, keySpec)

        override fun engineTranslateKey(key: Key): Key = platform().translateKey(key)

        private fun platform(): KeyFactory {
            for (provider in Security.getProviders()) {
                if (provider.name == PROVIDER_NAME) continue
                if (provider.getService("KeyFactory", "RSA") != null) {
                    return KeyFactory.getInstance("RSA", provider)
                }
            }
            throw IllegalStateException("No platform RSA KeyFactory available")
        }
    }

    private fun crtSpec(pkcs8: ByteArray): RSAPrivateCrtKeySpec {
        val outer = Der(pkcs8)
        outer.readHeader() // outer SEQUENCE
        outer.skip() // version
        outer.skip() // algorithm identifier
        val inner = Der(outer.octetString())
        inner.readHeader() // RSAPrivateKey SEQUENCE
        inner.skip() // version
        return RSAPrivateCrtKeySpec(
            inner.integer(),
            inner.integer(),
            inner.integer(),
            inner.integer(),
            inner.integer(),
            inner.integer(),
            inner.integer(),
            inner.integer(),
        )
    }

    private class Der(private val data: ByteArray) {
        private var pos = 0
        private var length = 0

        fun readHeader() {
            val tag = data[pos++].toInt() and 0xFF
            check(tag == 0x30 || tag == 0x02 || tag == 0x04) { "unexpected tag $tag" }
            var len = data[pos++].toInt() and 0xFF
            if (len and 0x80 != 0) {
                val count = len and 0x7F
                check(count in 1..3) { "unsupported length" }
                len = 0
                repeat(count) { len = (len shl 8) or (data[pos++].toInt() and 0xFF) }
            }
            length = len
        }

        fun skip() {
            readHeader()
            pos += length
        }

        fun octetString(): ByteArray {
            readHeader()
            val out = data.copyOfRange(pos, pos + length)
            pos += length
            return out
        }

        fun integer(): BigInteger {
            readHeader()
            val out = data.copyOfRange(pos, pos + length)
            pos += length
            return BigInteger(out)
        }
    }
}
