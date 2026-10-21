package com.akivnah.services.mail;

import com.akivnah.services.model.UserAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MailMessageRepository extends JpaRepository<MailMessage, Long> {

    @Query("select m from MailMessage m where m.owner = :o and m.folder = :f order by m.createdAt desc")
    List<MailMessage> inFolder(@Param("o") UserAccount o, @Param("f") MailFolder f, Pageable p);

    @Query("select m from MailMessage m where m.owner = :o and m.folder = :f and (lower(m.subject) like :q "
         + "or lower(m.fromAddress) like :q or lower(m.toAddresses) like :q or lower(m.body) like :q) order by m.createdAt desc")
    List<MailMessage> search(@Param("o") UserAccount o, @Param("f") MailFolder f, @Param("q") String q, Pageable p);

    @Query("select m from MailMessage m where m.owner = :o and m.starred = true and m.folder <> :trash order by m.createdAt desc")
    List<MailMessage> starred(@Param("o") UserAccount o, @Param("trash") MailFolder trash, Pageable p);

    long countByOwnerAndFolderAndReadFlagFalse(UserAccount owner, MailFolder folder);
    long countByOwnerAndFolder(UserAccount owner, MailFolder folder);
    boolean existsByOwnerAndExternalId(UserAccount owner, String externalId);
    List<MailMessage> findByFolderAndSendStatus(MailFolder folder, SendStatus status);

    @Modifying
    @Query("delete from MailMessage m where m.owner = :o and m.folder = :f")
    void deleteAllInFolder(@Param("o") UserAccount o, @Param("f") MailFolder f);

    @Modifying
    @Query("delete from MailMessage m where m.owner = :o")
    void deleteAllOwnedBy(@Param("o") UserAccount o);
}
