package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.constants.ChatMode;
import org.nobilis.nobichat.model.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID>, JpaSpecificationExecutor<Template> {

    @Query("SELECT t FROM Template t WHERE t.mode = :mode")
    List<Template> findAllByMode(@Param("mode") ChatMode mode);

    @Query(value = "SELECT * FROM templates t " +
            "WHERE t.schema -> 'view' ->> 'id' = :viewId " +
            "AND t.schema -> 'view' ->> 'version' = :viewVersion " +
            "AND t.mode = :modeName",
            nativeQuery = true)
    Optional<Template> findByViewIdAndVersionInSchemaAndMode(
                                                              @Param("viewId") String viewId,
                                                              @Param("viewVersion") String viewVersion,
                                                              @Param("modeName") String modeName);

    @Query(value = """
            SELECT * FROM (
                SELECT t.*,
                       DENSE_RANK() OVER (
                           PARTITION BY (t.schema -> 'view' ->> 'id') 
                           ORDER BY
                               CASE
                                   WHEN t.schema -> 'view' ->> 'version' ~ '^\\d+(\\.\\d+)*$' THEN string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] 
                                   ELSE '{0}'::int[]
                               END DESC
                       ) as rnk
                FROM templates t
                WHERE t.schema -> 'view' ->> 'id' IS NOT NULL 
                  AND t.mode = :modeName
            ) ranked_templates
            WHERE rnk = 1
            """, nativeQuery = true)
    List<Template> findAllLatestVersionsOfTemplatesInSchemaAndMode(
            @Param("modeName") String modeName);

    @Query(value = "SELECT * FROM templates t " +
            "WHERE t.schema -> 'view' ->> 'id' = :viewId " +
            "AND t.mode = :modeName " +
            "ORDER BY string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] DESC, t.last_updated_date DESC",
            nativeQuery = true)
    List<Template> findByViewIdInSchemaAndModeOrderByVersionDesc(
                                                                  @Param("viewId") String viewId,
                                                                  @Param("modeName") String modeName);

    @Query(value = "SELECT * FROM templates t WHERE t.schema -> 'view' ->> 'id' = :viewId", nativeQuery = true)
    Optional<Template> findByViewIdInSchema(@Param("viewId") String viewId);

    @Query(value = "SELECT * FROM templates t " +
            "WHERE t.schema -> 'view' ->> 'id' = :viewId " +
            "ORDER BY string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] DESC, t.last_updated_date DESC",
            nativeQuery = true)
    List<Template> findByViewIdInSchemaOrderByVersionDesc(@Param("viewId") String viewId);


    @Query(value = "SELECT * FROM templates t " +
            "WHERE t.schema -> 'view' ->> 'id' = :viewId AND t.schema -> 'view' ->> 'version' = :viewVersion",
            nativeQuery = true)
    Optional<Template> findByViewIdAndVersionInSchema(@Param("viewId") String viewId, @Param("viewVersion") String viewVersion);


    @Query(value = """
            SELECT * FROM (
                SELECT t.*,
                       DENSE_RANK() OVER (
                           PARTITION BY (t.schema -> 'view' ->> 'id')
                           ORDER BY
                               CASE
                                   WHEN t.schema -> 'view' ->> 'version' ~ '^\\d+(\\.\\d+)*$' THEN string_to_array(t.schema -> 'view' ->> 'version', '.')::int[]
                                   ELSE '{0}'::int[]
                               END DESC
                       ) as rnk
                FROM templates t
                WHERE t.schema -> 'view' ->> 'id' IS NOT NULL
            ) ranked_templates
            WHERE rnk = 1
            """, nativeQuery = true)
    List<Template> findAllLatestVersionsOfTemplatesInSchema();

    @Query(value = """
            SELECT * FROM (
                SELECT t.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY (t.schema -> 'view' ->> 'id')
                           ORDER BY string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] DESC, t.last_updated_date DESC
                       ) as rn
                FROM templates t
                WHERE t.mode = 'STRICT' AND t.schema -> 'view' ->> 'id' IS NOT NULL
            ) ranked_templates
            WHERE rn = 1
            """, nativeQuery = true)
    List<Template> findAllLatestStrict();


    @Query(value = """
            SELECT * FROM templates t
            WHERE t.mode = 'STRICT' AND t.schema -> 'view' ->> 'id' = :viewId
            ORDER BY string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] DESC, t.last_updated_date DESC
            """, nativeQuery = true)
    List<Template> findAllStrictByViewId(@Param("viewId") String viewId);

    /**
     * Находит уникальный шаблон в режиме STRICT по viewId и версии.
     */

    @Query(value = """
            SELECT * FROM templates t
            WHERE t.mode = 'STRICT'
              AND t.schema -> 'view' ->> 'id' = :viewId
              AND t.schema -> 'view' ->> 'version' = :viewVersion
            """, nativeQuery = true)
    Optional<Template> findStrictByViewIdAndVersion(@Param("viewId") String viewId, @Param("viewVersion") String viewVersion);


    @Query(value = """
            SELECT * FROM templates t
            WHERE t.mode = 'STRICT' AND t.schema -> 'view' ->> 'id' = :viewId
            ORDER BY string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] DESC, t.last_updated_date DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Template> findLatestStrictByViewId(@Param("viewId") String viewId);


    @Query(value = """
            SELECT * FROM (
                SELECT t.*,
                       DENSE_RANK() OVER (
                           PARTITION BY (t.schema -> 'view' ->> 'id') 
                           ORDER BY string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] DESC
                       ) as rnk
                FROM templates t
                WHERE t.mode = 'SOFT' AND t.schema -> 'view' ->> 'id' IS NOT NULL
            ) ranked_templates
            WHERE rnk = 1
            """, nativeQuery = true)
    List<Template> findAllLatestSoft();

    /**
     * Возвращает ВСЕ версии шаблонов для указанного viewId в режиме SOFT, отсортированные от новой к старой.
     */

    @Query(value = """
            SELECT * FROM templates t
            WHERE t.mode = 'SOFT' AND t.schema -> 'view' ->> 'id' = :viewId
            ORDER BY string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] DESC, t.last_updated_date DESC
            """, nativeQuery = true)
    List<Template> findAllSoftByViewId(@Param("viewId") String viewId);


    @Query(value = """
            SELECT * FROM templates t
            WHERE t.mode = 'SOFT'
              AND t.schema -> 'view' ->> 'id' = :viewId
              AND t.schema -> 'view' ->> 'version' = :viewVersion
            """, nativeQuery = true)
    List<Template> findSoftByViewIdAndVersion(@Param("viewId") String viewId, @Param("viewVersion") String viewVersion);


    @Query(value = """
            SELECT * FROM (
                SELECT t.*,
                       DENSE_RANK() OVER (
                           ORDER BY string_to_array(t.schema -> 'view' ->> 'version', '.')::int[] DESC
                       ) as rnk
                FROM templates t
                WHERE t.mode = 'SOFT' AND t.schema -> 'view' ->> 'id' = :viewId
            ) ranked_templates
            WHERE rnk = 1
            """, nativeQuery = true)
    List<Template> findLatestSoftByViewId(@Param("viewId") String viewId);
}