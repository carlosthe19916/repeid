package io.github.carlosthe19916.repeid.repository;

import io.github.carlosthe19916.repeid.model.Version;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class VersionRepositoryImpl implements VersionRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<Version> getLastVersion() {
        TypedQuery<Version> query = em.createNamedQuery("getVersions", Version.class);
        query.setMaxResults(1);
        List<Version> resultList = query.getResultList();
        if (resultList.size() == 1) {
            return Optional.of(resultList.get(0));
        }
        return Optional.empty();
    }

}