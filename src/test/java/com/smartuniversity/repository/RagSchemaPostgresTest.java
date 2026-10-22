package com.smartuniversity.repository;

import com.smartuniversity.model.DocumentChunk;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.service.rag.VectorMath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production runs Hibernate with ddl-auto=validate, so the RAG tables come from
 * db/rag-schema.sql instead of from Hibernate. On a real PostgreSQL this checks that the script
 * can run on every restart and that Hibernate then accepts the RAG entities and can store them.
 */
class RagSchemaPostgresTest {

    @Test
    void schemaScriptSatisfiesHibernateValidationOnPostgres() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            ResourceDatabasePopulator script = new ResourceDatabasePopulator(new ClassPathResource("db/rag-schema.sql"));
            script.execute(dataSource);
            script.execute(dataSource); // as on a restart

            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
                    .applySetting(AvailableSettings.HBM2DDL_AUTO, "validate")
                    // The naming Spring Boot applies, so table and column names match production
                    .applySetting(AvailableSettings.PHYSICAL_NAMING_STRATEGY, CamelCaseToUnderscoresNamingStrategy.class.getName())
                    .applySetting(AvailableSettings.IMPLICIT_NAMING_STRATEGY, SpringImplicitNamingStrategy.class.getName())
                    .build();

            // Building the session factory runs the validation and throws if the schema doesn't match.
            try (SessionFactory sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(IndexedDocument.class)
                    .addAnnotatedClass(DocumentChunk.class)
                    .buildMetadata()
                    .buildSessionFactory()) {

                float[] vector = VectorMath.normalize(new float[]{1, 2, 3});
                Long chunkId;
                try (Session session = sessionFactory.openSession()) {
                    Transaction transaction = session.beginTransaction();
                    IndexedDocument document = new IndexedDocument();
                    document.setS3Key("chatbot/notes.pdf");
                    document.setPagesIndexed(1);
                    document.setTotalPages(1);
                    document.setChunkCount(1);
                    document.setEmbeddingModel("gemini-embedding-2/3");
                    session.persist(document);
                    DocumentChunk chunk = new DocumentChunk(document, 1, 0, "Base cases stop recursion.", VectorMath.toBytes(vector));
                    session.persist(chunk);
                    transaction.commit();
                    chunkId = chunk.getId();
                }

                try (Session session = sessionFactory.openSession()) {
                    DocumentChunk stored = session.get(DocumentChunk.class, chunkId);
                    assertEquals("Base cases stop recursion.", stored.getContent());
                    assertArrayEquals(vector, VectorMath.fromBytes(stored.getEmbedding()));
                }
            }
        }
    }
}
