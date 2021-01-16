package io.github.project.openubl.searchpe.managers;

import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.utils.DataHelper;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

@ApplicationScoped
public class UpgradeDataManager {

    private static final Logger LOGGER = Logger.getLogger(UpgradeDataManager.class);

    @ConfigProperty(name = "quarkus.hibernate-orm.jdbc.statement-batch-size", defaultValue = "1000")
    Integer jdbcBatchSize;

    @Inject
    FileManager fileManager;

    @Inject
    UserTransaction tx;

    @Inject
    EntityManager entityManager;

    public void upgrade() {
        File downloadedFile;
        File unzippedFolder;
        File txtFile;

        // Download file
        try {
            downloadedFile = fileManager.downloadFile();

            unzippedFolder = fileManager.unzip(downloadedFile);
            txtFile = fileManager.getFirstTxtFileFound(unzippedFolder.listFiles());
        } catch (IOException e) {
            LOGGER.error(e);
            return;
        }

        // Persist data
        try {
            createContribuyentesFromFile(txtFile);
        } catch (IOException e) {
            LOGGER.error(e);
            return;
        }

        // Clear folders
        try {
            LOGGER.infof("Deleting directory %s", downloadedFile.toString());
            LOGGER.infof("Deleting directory %s", unzippedFolder.toString());
            downloadedFile.delete();
            FileUtils.deleteDirectory(unzippedFolder);
        } catch (IOException e) {
            LOGGER.error(e);
            return;
        }
    }

    public void createContribuyentesFromFile(File file) throws IOException {
        VersionEntity version;

        try {
            tx.begin();

            version = VersionEntity.Builder.aVersionEntity()
                    .withActive(false)
                    .withCreatedAt(new Date())
                    .withStatus(Status.SCHEDULED)
                    .build();
            version.persist();

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                LOGGER.error(se);
            }
            return;
        }

        LOGGER.infof("Start importing contribuyentes");
        long startTime = Calendar.getInstance().getTimeInMillis();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean skip = true;

            int batchSize = jdbcBatchSize;

            tx.begin();
            int cont = 0;
            while ((line = br.readLine()) != null) {
                if (skip) {
                    skip = false;
                    continue;
                }

                String[] columns = DataHelper.readLine(line, 15);
                ContribuyenteEntity contribuyente = ContribuyenteEntity
                        .Builder.aContribuyenteEntity()
                        .withVersion(version)
                        .withId(version.id + "-" + columns[0])
                        .withRuc(columns[0])
                        .withRazonSocial(columns[1])
                        .withEstadoContribuyente(columns[2])
                        .withCondicionDomicilio(columns[3])
                        .withUbigeo(columns[4])
                        .withTipoVia(columns[5])
                        .withNombreVia(columns[6])
                        .withCodigoZona(columns[7])
                        .withTipoZona(columns[8])
                        .withNumero(columns[9])
                        .withInterior(columns[10])
                        .withLote(columns[11])
                        .withDepartamento(columns[12])
                        .withManzana(columns[13])
                        .withKilometro(columns[14])
                        .build();

                entityManager.persist(contribuyente);
                cont++;
                if (cont % batchSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                    tx.commit();
                    tx.begin();
                }
            }

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | SystemException | RollbackException | HeuristicMixedException e) {
            LOGGER.error(e);
            return;
        }


        try {
            tx.begin();

            version = VersionEntity.findById(version.id);
            version.status = Status.COMPLETED;
            version.active = true;

            VersionEntity.persist(version);

            tx.commit();
        } catch (NotSupportedException | HeuristicRollbackException | HeuristicMixedException | RollbackException | SystemException e) {
            try {
                tx.rollback();
            } catch (SystemException se) {
                LOGGER.error(se);
            }
            return;
        }

        long endTime = Calendar.getInstance().getTimeInMillis();
        LOGGER.infof("Import contribuyentes finished successfully in" + (endTime - startTime) + " milliseconds.");
    }

}