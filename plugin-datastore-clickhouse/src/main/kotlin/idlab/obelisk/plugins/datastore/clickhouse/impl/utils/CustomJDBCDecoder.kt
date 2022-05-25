package idlab.obelisk.plugins.datastore.clickhouse.impl.utils

import com.google.auto.service.AutoService
import io.vertx.ext.jdbc.impl.actions.SQLValueProvider
import io.vertx.ext.jdbc.spi.JDBCColumnDescriptorProvider
import io.vertx.ext.jdbc.spi.JDBCDecoder
import io.vertx.ext.jdbc.spi.impl.JDBCDecoderImpl
import io.vertx.jdbcclient.impl.actions.JDBCColumnDescriptor
import java.sql.CallableStatement
import java.sql.ResultSet

//@AutoService(JDBCDecoder::class)
class CustomJDBCDecoder : JDBCDecoderImpl() {
    override fun parse(p0: ResultSet?, p1: Int, p2: JDBCColumnDescriptorProvider?): Any? {
        return super.parse(p0, p1, p2)
    }

    override fun parse(p0: CallableStatement?, p1: Int, p2: JDBCColumnDescriptorProvider?): Any? {
        return super.parse(p0, p1, p2)
    }

    override fun decode(p0: JDBCColumnDescriptor?, p1: SQLValueProvider?): Any? {
        return super.decode(p0, p1)
    }

    override fun cast(p0: Any?): Any? {
        return super.cast(p0)
    }

    override fun decodeUnhandledType(valueProvider: SQLValueProvider?, descriptor: JDBCColumnDescriptor?): Any? {
        return super.decodeUnhandledType(valueProvider, descriptor)
    }

    override fun decodeSpecificVendorType(valueProvider: SQLValueProvider?, descriptor: JDBCColumnDescriptor?): Any? {
        return super.decodeSpecificVendorType(valueProvider, descriptor)
    }
}
