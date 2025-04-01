package ro.unibuc.hello.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import ro.unibuc.hello.dto.review.ReviewRequestDTO;
import ro.unibuc.hello.dto.review.ReviewResponseDTO;
import ro.unibuc.hello.enums.RideStatus;
import ro.unibuc.hello.enums.RideBookingStatus;
import ro.unibuc.hello.model.Review;
import ro.unibuc.hello.model.RideBooking;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.model.Ride;
import ro.unibuc.hello.repository.ReviewRepository;

import ro.unibuc.hello.exceptions.review.InvalidReviewException;

import ro.unibuc.hello.repository.RideRepository;
import ro.unibuc.hello.repository.UserRepository;
import ro.unibuc.hello.repository.RideBookingRepository;

@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final RideRepository rideRepository;
    private final UserRepository userRepository;
    private final RideBookingRepository rideBookingRepository;

    public ReviewService(ReviewRepository reviewRepository, 
                        RideRepository rideRepository, 
                        UserRepository userRepository,
                        RideBookingRepository rideBookingRepository
                        ) {
        this.reviewRepository = reviewRepository;
        this.rideRepository = rideRepository;
        this.userRepository = userRepository;
        this.rideBookingRepository = rideBookingRepository;
    }

    public void deleteAllReviews() {
        reviewRepository.deleteAll();
    }

    public Review saveReview(Review review) {
        return reviewRepository.save(review);
    }

    public List<Review> getReviewsByRide(String id) {
        return reviewRepository.findByRideId(id);
    }

    public List<Review> getReviewsByDriver(String id) {
        return reviewRepository.findByReviewedId(id);
    }

    public ReviewResponseDTO createReview(ReviewRequestDTO reviewRequestDTO) {
        // Check if reviewer exists in users table
        if (!userRepository.existsById(reviewRequestDTO.getReviewerId())) {
            throw new InvalidReviewException("Reviewer does not exist as user.");
        }

         // Check if reviewed exists in users table
         if (!userRepository.existsById(reviewRequestDTO.getReviewedId())) {
            throw new InvalidReviewException("Reviewed does not exist as user.");
        }

        // Check if reviewer is different from reviewed
        if (reviewRequestDTO.getReviewerId().equals(reviewRequestDTO.getReviewedId())) {
            throw new InvalidReviewException("Reviewer can't also be reviewed.");
        }

        Optional<Ride> rideOptional = rideRepository.findById(reviewRequestDTO.getRideId());
        // Check if ride exists in rides table
        if (rideOptional.isEmpty()) {
            throw new InvalidReviewException("Ride does not exist.");
        } 

        Ride ride = rideOptional.get();
        // Check if ride status is completed
        if (!ride.getStatus().equals(RideStatus.COMPLETED)) {
            throw new InvalidReviewException("Ride is not completed.");
        }


        RideBooking rideBooking = rideBookingRepository.findByRideIdAndPassengerId(
            reviewRequestDTO.getRideId(), reviewRequestDTO.getReviewerId()
            ).orElse(null);

        // Check if reviewer is passenger
        if (rideBooking == null) {
            throw new InvalidReviewException("Reviewer is not a passenger.");
        }

        // Check if reviewer cancelled the ride
        if (rideBooking.getRideBookingStatus().equals(RideBookingStatus.CANCELLED)) {
            throw new InvalidReviewException("Reviewer cancelled ride.");
        }

        Optional<Review> existingReview = reviewRepository.findByRideIdAndReviewerId(
            reviewRequestDTO.getRideId(), reviewRequestDTO.getReviewerId());

        // Check if reviewer already reviewed ride
        if (existingReview.isPresent()) {
            throw new InvalidReviewException("Reviewer already made a review for this ride");
        }

        
        // Check if reviewed is driver of ride
        if (!ride.getDriverId().equals(reviewRequestDTO.getReviewedId())) {
            throw new InvalidReviewException("Reviewed is not driver of ride");
        }

        Review newReview = reviewRequestDTO.toEntity();
        User reviewed = userRepository.findById(reviewRequestDTO.getReviewedId()).get();
        
        reviewed.setRatingsSum(reviewed.getRatingsSum() + reviewRequestDTO.getRating());
        reviewed.setReviewsNumber(reviewed.getReviewsNumber() + 1);

        reviewed.setAvgRating((double) reviewed.getRatingsSum() / reviewed.getReviewsNumber());

        reviewRepository.save(newReview);
        userRepository.save(reviewed);

        return ReviewResponseDTO.toDTO(newReview);

    }
}
