package idlab.obelisk.utils.service.mail

import idlab.obelisk.definitions.catalog.User
import idlab.obelisk.definitions.framework.OblxConfig
import io.reactivex.Completable
import io.vertx.ext.mail.MailMessage
import io.vertx.reactivex.ext.mail.MailClient
import mu.KotlinLogging
import java.net.URI

interface MailService {

    fun send(recipients: Set<User>, title: String, message: String, referralPath: String? = null): Completable

}

internal class SmtpMailService(private val mailClient: MailClient, private val config: OblxConfig) : MailService {

    private val logger = KotlinLogging.logger {}
    private val senderAddress = "no-reply@${URI(config.authPublicUri).host}"

    override fun send(recipients: Set<User>, title: String, message: String, referralPath: String?): Completable {
        val recipientsAddrs = recipients
            .filterNot {
                // Filter out the "0" User aka the built-in admin account
                it.id == "0"
            }
            .map { it.notificationAddress ?: it.email }
        if (recipientsAddrs.isEmpty()) {
            return Completable.complete()
        } else {
            val html = """
        <html>
            <body>
                <p>$message</p>
                <a href="${config.authPublicUri.trimEnd('/')}$referralPath"><button>View in Obelisk</button></a>
            </body>
        </html>
    """.trimIndent()
            return mailClient.rxSendMail(
                MailMessage().setTo(recipientsAddrs).setFrom(senderAddress).setSubject(title).setHtml(html)
            )
                .ignoreElement()
                .onErrorComplete { err ->
                    // Log and complete anyway
                    logger.warn(err) { "Could not send email to $recipientsAddrs" }
                    true
                }
        }
    }

}