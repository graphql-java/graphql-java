package graphql.execution.instrumentation.dataloader;


import org.dataloader.DataLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class DataLoaderCompanyProductBackend {

    private final ConcurrentMap<UUID, Company> companies = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Project> projects = new ConcurrentHashMap<>();

    private final DataLoader<UUID, List<Project>> projectsLoader;

    public DataLoaderCompanyProductBackend(int companyCount, int projectCount) {
        for (int i = 0; i < companyCount; i++) {
            mkCompany(projectCount);
        }

        projectsLoader = new DataLoader<>(keys -> getProjectsForCompanies(keys).thenApply(projects -> keys
                .stream()
                .map(companyId -> projects.stream()
                        .filter(project -> project.getCompanyId().equals(companyId))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList())));

    }

    private Company mkCompany(int projectCount) {
        Company company = new Company();
        companies.put(company.getId(), company);
        for (int j = 0; j < projectCount; j++) {
            Project project = new Project(company.getId());
            projects.put(project.getId(), project);
        }
        return company;
    }

    public DataLoader<UUID, List<Project>> getProjectsLoader() {
        return projectsLoader;
    }

    public CompletableFuture<List<Company>> getCompanies() {
        return CompletableFuture.supplyAsync(this::companiesList);
    }

    private List<Company> companiesList() {
        return Collections.unmodifiableList(new ArrayList<>(companies.values()));
    }

    public CompletableFuture<List<Project>> getProjectsForCompanies(List<UUID> companyIds) {
        return CompletableFuture.supplyAsync(() -> projects.values().stream()
                .filter(project -> companyIds.contains(project.getCompanyId()))
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
    }

    public CompletableFuture<Company> addCompany() {
        return CompletableFuture.supplyAsync(() -> mkCompany(3));
    }

    public static class Company {

        private final UUID id;
        private final String name;

        public Company() {
            id = UUID.randomUUID();
            name = "Company " + id.toString().substring(0, 8);
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

    }

    public static class Project {

        private final UUID id;
        private final String name;
        private final UUID companyId;

        public Project(UUID companyId) {
            id = UUID.randomUUID();
            name = "Project " + id.toString().substring(0, 8);
            this.companyId = requireNonNull(companyId);
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public UUID getCompanyId() {
            return companyId;
        }

    }

}