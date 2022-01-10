package com.practice.onlineShop.handlers;

import com.practice.onlineShop.exceptions.InvalidProductCodeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static org.springframework.http.ResponseEntity.status;

@ControllerAdvice
public class ProductHandler {

    @ExceptionHandler(InvalidProductCodeException.class)
    public ResponseEntity<String> handleInvalidCustomerIdException(){
        return status(HttpStatus.BAD_REQUEST).body("Codul produsului trimis este invalid!");
    }

}
