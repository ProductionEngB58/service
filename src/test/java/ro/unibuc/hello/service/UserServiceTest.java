package ro.unibuc.hello.service;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;


import ro.unibuc.hello.dto.user.UserRequestDTO;
import ro.unibuc.hello.dto.user.UserResponseDTO;
import ro.unibuc.hello.exceptions.user.InvalidUserException;
import ro.unibuc.hello.model.User;
import ro.unibuc.hello.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

   @Test 
   void testGetAll() {
    List<User> users = Arrays.asList(
        new User("Andrei", "Popescu", "andrei@gmail.com", "0787828282", new ArrayList<>()),
        new User("Marius", "Ivan", "ivan@gmail.com", "0712354324", new ArrayList<>())
    );
    when(userRepository.findAll()).thenReturn(users);

    List<User> result = userService.getAllUsers();

    // Assert size
    assertEquals(2, result.size());

    // Assert name
    assertEquals("Andrei", result.get(0).getFirstName());
    assertEquals("Ivan", result.get(1).getLastName());

    // Assert mail
    assertEquals("andrei@gmail.com", result.get(0).getMail());

    // Assert phone number
    assertEquals("0712354324", result.get(1).getPhoneNumber());

    // Assert Rating = 0.0
    assertEquals(0.0, result.get(0).getAvgRating());

   }

   @Test
   void testGetById() {

    User user = new User("Andrei", "Popescu", "andrei@gmail.com", "0787828282", new ArrayList<>());

    when(userRepository.findById("1")).thenReturn(Optional.of(user));

    UserResponseDTO result = userService.getUserById("1");

    // Assert name
    assertEquals("Andrei", result.getFirstName());
    assertEquals("Popescu", result.getLastName());

    // Assert mail
    assertEquals("andrei@gmail.com", result.getMail());

    // Assert phone number
    assertEquals("0787828282", result.getPhoneNumber());

    // Assert Rating = 0.0
    assertEquals(0.0, result.getAvgRating());
   }

   @Test
   void testGetByMail() {

    User user = new User("Andrei", "Popescu", "andrei@gmail.com", "0787828282", new ArrayList<>());

    when(userRepository.findByMail("andrei@gmail.com")).thenReturn(Optional.of(user));

    UserResponseDTO result = userService.getUserByMail("andrei@gmail.com");

    // Assert name
    assertEquals("Andrei", result.getFirstName());
    assertEquals("Popescu", result.getLastName());

    // Assert mail
    assertEquals("andrei@gmail.com", result.getMail());

    // Assert phone number
    assertEquals("0787828282", result.getPhoneNumber());

    // Assert Rating = 0.0
    assertEquals(0.0, result.getAvgRating());
   }

   @Test
   void testUpdateUsername_Success() {

    User userBefore = new User("Andrei", "Popescu", "andrei@gmail.com", "0787828282", new ArrayList<>());
    User userAfter = new User("Razvan", "Leclerc", "andrei@gmail.com", "0787828282", new ArrayList<>());

    when(userRepository.findById("1")).thenReturn(Optional.of(userBefore));
    when(userRepository.save(any(User.class))).thenReturn(userAfter);

    UserResponseDTO result = userService.updateUserName("1", "Andrei", "Popescu");

    // Assert name
    assertEquals("Razvan", result.getFirstName());
    assertEquals("Leclerc", result.getLastName());

    // Assert mail
    assertEquals("andrei@gmail.com", result.getMail());

    // Assert phone number
    assertEquals("0787828282", result.getPhoneNumber());

    // Assert Rating = 0.0
    assertEquals(0.0, result.getAvgRating());
   }

   @Test
   void testUpdateUsername_UserNotFound() {

    User userBefore = new User("Andrei", "Popescu", "andrei@gmail.com", "0787828282", new ArrayList<>());
    User userAfter = new User("Razvan", "Leclerc", "andrei@gmail.com", "0787828282", new ArrayList<>());

    when(userRepository.findById("1")).thenReturn(Optional.empty());

    assertThrows(InvalidUserException.class, () -> {
        userService.updateUserName("1", "Andrei", "Popescu");
    });

    verify(userRepository, times(1)).findById("1");
    verify(userRepository, times(0)).save(any());

   }

   @Test 
   void testCreateUser_Success() {

    UserRequestDTO newUser = new UserRequestDTO("Andrei", "Popescu", "andrei@gmail.com", "0787828282", "parolamea");

    when(userRepository.findByMail(newUser.getMail())).thenReturn(Optional.empty());
    when(userRepository.findByPhoneNumber(newUser.getPhoneNumber())).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenReturn(newUser.toEntity());

    UserResponseDTO result = userService.createUser(newUser);

    assertEquals(newUser.getFirstName(), result.getFirstName());
    assertEquals(newUser.getLastName(), result.getLastName());
    assertEquals(newUser.getMail(), result.getMail());
    assertEquals(newUser.getPhoneNumber(), result.getPhoneNumber());

   }

   @Test 
   void testCreateUser_MailDuplicate() {

    UserRequestDTO newUser = new UserRequestDTO("Andrei", "Popescu", "andrei@gmail.com", "0787828282", "parolamea");

    when(userRepository.findByMail(newUser.getMail())).thenReturn(Optional.of(newUser.toEntity()));
    assertThrows(DuplicateKeyException.class, () -> {
        UserResponseDTO result = userService.createUser(newUser);
    });

    verify(userRepository, times(0)).save(any());
    verify(userRepository, times(1)).findByMail(newUser.getMail());
    verify(userRepository, times(0)).findByPhoneNumber(newUser.getPhoneNumber());

   }

   @Test 
   void testCreateUser_PhoneDuplicate() {

    UserRequestDTO newUser = new UserRequestDTO("Andrei", "Popescu", "andrei@gmail.com", "0787828282", "parolamea");

    when(userRepository.findByMail(newUser.getMail())).thenReturn(Optional.empty());
    when(userRepository.findByPhoneNumber(newUser.getPhoneNumber())).thenReturn(Optional.of(newUser.toEntity()));
    assertThrows(DuplicateKeyException.class, () -> {
        UserResponseDTO result = userService.createUser(newUser);
    });

    verify(userRepository, times(0)).save(any());
    verify(userRepository, times(1)).findByMail(newUser.getMail());
    verify(userRepository, times(1)).findByPhoneNumber(newUser.getPhoneNumber());

   }


}
