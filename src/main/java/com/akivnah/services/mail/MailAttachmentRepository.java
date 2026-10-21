package com.akivnah.services.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MailAttachmentRepository extends JpaRepository<MailAttachment, Long> {

    @Query("select new com.akivnah.services.mail.AttachmentInfo(a.id, a.filename, a.size) from MailAttachment a "
         + "where a.message.id = :mid order by a.id")
    List<AttachmentInfo> infos(@Param("mid") Long messageId);

    @Query("select distinct a.message.id from MailAttachment a where a.message.id in :ids")
    List<Long> idsWithFiles(@Param("ids") Collection<Long> ids);

    List<MailAttachment> findByMessageId(Long messageId);
    long countByMessageId(Long messageId);

    @Query("select coalesce(sum(a.size), 0) from MailAttachment a where a.message.id = :mid")
    long totalSize(@Param("mid") Long messageId);

    @Modifying
    @Query("delete from MailAttachment a where a.id in :ids and a.message.id = :mid")
    void deleteByIds(@Param("ids") Collection<Long> ids, @Param("mid") Long messageId);

    @Modifying
    @Query("delete from MailAttachment a where a.message in (select m from MailMessage m where m.owner = :o)")
    void deleteAllOwnedBy(@Param("o") com.akivnah.services.model.UserAccount o);
}
