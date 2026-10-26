package com.example.ticketbooking.vehicle;

import jakarta.persistence.*;

@Entity
@Table(name = "seats")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;
    @Column(name = "seat_code", nullable = false, length = 20)
    private String seatCode;
    @Column(name = "seat_class", nullable = false, length = 40)
    private String seatClass;
    @Column(name = "coach_number")
    private Integer coachNumber;
    @Column(name = "compartment_number")
    private Integer compartmentNumber;
    @Column(name = "deck_number")
    private Integer deckNumber;
    @Column(name = "position_number")
    private Integer positionNumber;
    protected Seat() { }
    public Seat(Vehicle vehicle, SeatRequest request) {
        this.vehicle = vehicle;
        update(request);
    }
    public void update(SeatRequest request) {
        seatCode = request.seatCode();
        seatClass = request.seatClass();
    }
    public Long getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public String getSeatCode() { return seatCode; }
    public String getSeatClass() { return seatClass; }
    public Integer getCoachNumber() { return coachNumber; }
    public Integer getCompartmentNumber() { return compartmentNumber; }
    public Integer getDeckNumber() { return deckNumber; }
    public Integer getPositionNumber() { return positionNumber; }
    public void setPosition(int coach, Integer compartment, int deck, int position) {
        coachNumber = coach;
        compartmentNumber = compartment;
        deckNumber = deck;
        positionNumber = position;
    }
}
