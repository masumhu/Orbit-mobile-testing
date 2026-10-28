package com.devhub.repository;

import com.devhub.ticket.Ticket;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface TicketRepository extends MongoRepository<Ticket, String> {
    List<Ticket> findByOrganizationId(String organizationId);
    List<Ticket> findByProjectId(String projectId);
    List<Ticket> findByAssignedSeniorManagerId(String userId);
    List<Ticket> findByAssignedDeveloperId(String userId);
}
