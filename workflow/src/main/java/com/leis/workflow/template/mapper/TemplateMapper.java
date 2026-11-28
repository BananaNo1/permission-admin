package com.leis.workflow.template.mapper;

import com.leis.workflow.template.entity.TemplateDraftRow;
import com.leis.workflow.template.entity.TemplateReleaseRow;
import com.leis.workflow.template.TemplateService.VersionSummary;
import com.leis.workflow.template.TemplateService.VersionSummary;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 访问流程模板草稿和版本快照，SQL 与结果映射定义在 TemplateMapper.xml。
 */
@Mapper
public interface TemplateMapper {
    /**
     * 按模板编码排序查询指定业务的草稿。
     */
    List<TemplateDraftRow> findDrafts(String business);

    /**
     * 按业务编码和模板编码读取并锁定草稿，供发布事务使用。
     */
    TemplateDraftRow lockDraft(String business, String code);

    /**
     * 插入模板的首个草稿修订，保存节点配置和画布布局。
     */
    void insertDraft(String business, String code, String engineKey, String name,
                     String steps, String diagramLayout, long editedBy);

    /**
     * 仅在修订号匹配时更新草稿并递增修订号，返回实际更新的行数。
     */
    int updateDraft(String business, String code, int revision, String name,
                    String steps, String diagramLayout, long editedBy);

    /**
     * 保存本次发布的配置快照和对应 Flowable 定义标识。
     */
    void insertRelease(String business, String code, int version, String name, String steps,
                       String diagramLayout, String definitionId, long publishedBy);

    /**
     * 更新当前发布版本，并推进草稿和已发布修订号。
     */
    void markPublished(String business, String code, int version);

    /**
     * 读取指定模板当前生效的发布快照。
     */
    TemplateReleaseRow findCurrentRelease(String business, String code);

    /**
     * 列出指定业务下当前生效的发布快照，按模板编码排序。
     */
    List<TemplateReleaseRow> findCurrentReleases(String business);

    /** 查询模板发布历史，最新版本排在最前。 */
    List<VersionSummary> findVersions(String business, String code);

    /** 按业务、模板和版本号读取不可变的发布快照。 */
    TemplateReleaseRow findVersion(String business, String code, int version);

    /** 使用修订号保护启停修改，并保持当前草稿的待发布状态。 */
    int updateAvailability(String business, String code, int revision, boolean enabled, long editedBy);
}
