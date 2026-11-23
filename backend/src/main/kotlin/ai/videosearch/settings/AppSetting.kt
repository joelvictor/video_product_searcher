package ai.videosearch.settings

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "app_settings")
class AppSetting : PanacheEntityBase {
    @Id
    @Column(name = "setting_key")
    lateinit var key: String

    @Column(name = "setting_value")
    lateinit var value: String

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()

    companion object : PanacheCompanionBase<AppSetting, String>
}
