package uk.co.telegraph.plugin.encryption.utils

import java.nio.ByteBuffer

import com.amazonaws.services.kms.{AWSKMS, AWSKMSClientBuilder}
import com.amazonaws.services.kms.model.{DecryptRequest, EncryptRequest}
import com.typesafe.config.{Config, ConfigFactory}

trait ConfigEncryptor {
  def encrypt(config: Config, configPath: String, key: String): Config

  def decrypt(config: Config, configPath: String, key: String): Config
}
class AWSConfigEncryptor(awskms: AWSKMS) extends ConfigEncryptor {
  type EncDecOperation = (ByteBuffer) => Config
  private def configEncDecOps(config: Config, configPath: String, op: EncDecOperation): Config = {
    val plainText = getConfigPlainText(config, configPath)
    replaceConfigField(config, configPath, op(ByteBuffer.wrap(plainText.getBytes())))
  }

  override def encrypt(config: Config, configPath: String, key: String): Config = {
    configEncDecOps(config, configPath, (plainTextBlob: ByteBuffer) => {
      val encryptRequest = new EncryptRequest()
        .withPlaintext(plainTextBlob)
        .withKeyId(key)
      val cipherConf = base64Encode(awskms.encrypt(encryptRequest).getCiphertextBlob())
      ConfigFactory.parseString(s"""{$configPath={$IsEncrypted = true, $EncryptedConfigField = "$cipherConf"}}""")
    })
  }

  override def decrypt(config: Config, configPath: String, key: String): Config = {
    configEncDecOps(config, configPath, (cipherTextBlob: ByteBuffer) => {
      val decryptRequest = new DecryptRequest()
        .withCiphertextBlob(cipherTextBlob)
      val plainText = base64Decode(awskms.decrypt(decryptRequest).getPlaintext())
//      val configPathArray = configPath.split('.')
//      val configPathExceptField = configPathArray.dropRight(1).mkString(".")
//      val field = configPathArray.last
      ConfigFactory.parseString(s"""{$configPath: $plainText}""")
    })
  }
}

object ConfigEncryptor {
  def apply(region: String = "eu-west-1"): ConfigEncryptor =
    new AWSConfigEncryptor(AWSKMSClientBuilder.standard().withRegion(region).build())
}