package com.example.ticketbooking.route;

import jakarta.persistence.*;

@Entity
@Table(name = "routes")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String origin;
    @Column(nullable = false, length = 160)
    private String destination;
    @Column(nullable = false)
    private boolean active;
    protected Route() { }
    public Route(RouteRequest request) {
        update(request);
    }
    public void update(RouteRequest request) {
        origin = request.origin();
        destination = request.destination();
        active = request.active();
    }
    public Long getId() { return id; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public boolean isActive() { return active; }
}
