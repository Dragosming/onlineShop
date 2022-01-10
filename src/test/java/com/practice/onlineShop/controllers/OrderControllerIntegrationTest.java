package com.practice.onlineShop.controllers;

import com.practice.onlineShop.entities.OrderItem;
import com.practice.onlineShop.entities.Orders;
import com.practice.onlineShop.entities.Product;
import com.practice.onlineShop.entities.User;
import com.practice.onlineShop.enums.Roles;
import com.practice.onlineShop.repositories.OrderRepository;
import com.practice.onlineShop.utils.UtilsComponent;
import com.practice.onlineShop.vos.OrderVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIntegrationTest {

    @TestConfiguration
    static class ProductControllerIntegrationTestContextConfiguration{
        @Bean
        public RestTemplate restTemplateForPatch(){
            return new RestTemplate(new HttpComponentsClientHttpRequestFactory());
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private RestTemplate restTemplateForPatch;

    @Autowired
    private UtilsComponent utilsComponent;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @Transactional
    public void addOrder_whenOrderIsValid_shouldAddItToDB(){
        User user = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("code1", "code2");

        OrderVO orderVO = createOrderVO(user, product);

        testRestTemplate.postForEntity(UtilsComponent.LOCALHOST + port + "/order", orderVO, Void.class);

        List<Orders> ordersIterable = (List<Orders>) orderRepository.findAll();
        Optional<OrderItem> orderItemOptional = ordersIterable.stream()
                .map(order -> (List<OrderItem>) order.getOrderItems())
                .flatMap(List::stream)
                .filter(orderItem -> orderItem.getProduct().getId() == product.getId())
                .findFirst();

        assertThat(orderItemOptional).isPresent();
    }



    @Test
    public void addOrder_whenRequestIsMadeByAdmin_shouldThrowAnException(){
        User user = utilsComponent.saveUserWithRole(Roles.ADMIN);
        Product product = utilsComponent.storeTwoProductsInDatabase("code1ForAdmin", "code2ForAdmin");

        OrderVO orderVO = createOrderVO(user, product);

        ResponseEntity<String> responseEntity = testRestTemplate.postForEntity(UtilsComponent.LOCALHOST + port
                + "/order", orderVO, String.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(responseEntity.getBody()).isEqualTo("Utilizatorul nu are persimiunea de a executa aceasta operatiune!");
    }

    @Test
    public void addOrder_whenRequestIsMadeByExpeditor_shouldThrowAnException(){
        User user = utilsComponent.saveUserWithRole(Roles.EXPEDITOR);
        Product product = utilsComponent.storeTwoProductsInDatabase("code1ForExpeditor", "code2ForExpeditor");

        OrderVO orderVO = createOrderVO(user, product);

        ResponseEntity<String> responseEntity = testRestTemplate.postForEntity(UtilsComponent.LOCALHOST + port
                + "/order", orderVO, String.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(responseEntity.getBody()).isEqualTo("Utilizatorul nu are persimiunea de a executa aceasta operatiune!");
    }

    @Test
    public void deliver_whenHavingAnOrderWhichIsNOTCanceled_shouldDeliverByExpeditor(){
        User expeditor = utilsComponent.saveUserWithRole(Roles.EXPEDITOR);
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("code1ForExpeditorForDeliver", "code2ForExpeditorForDeliver");

        Orders orderWithProducts = generateOrderItems(product, client);
        orderRepository.save(orderWithProducts);

        restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/" + orderWithProducts.getId() + "/"
                + expeditor.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class );

        Orders orderFromDB = orderRepository.findById(orderWithProducts.getId()).get();
        assertThat(orderFromDB.isDelivered()).isTrue();
    }



    @Test
    public void deliver_whenHavingAnOrderWhichIsNOTCanceled_shouldNOTDeliverByAdmin(){
        User adminAsExpeditor = utilsComponent.saveUserWithRole(Roles.ADMIN);
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("code1ForAdminForDeliver", "code2ForAdminForDeliver");

        Orders orderWithProducts = generateOrderItems(product, client);
        orderRepository.save(orderWithProducts);
        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/" + orderWithProducts.getId() + "/"
                    + adminAsExpeditor.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, String.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Utilizatorul nu are persimiunea de a executa aceasta operatiune!]");
        }

    }

    @Test
    public void deliver_whenHavingAnOrderWhichIsNOTCanceled_shouldNOTDeliverByClient(){
        User clientAsExpeditor = utilsComponent.saveUserWithRole(Roles.CLIENT);
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("code1ForClientForDeliver", "code2ForClientForDeliver");

        Orders orderWithProducts = generateOrderItems(product, client);
        orderRepository.save(orderWithProducts);
        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/" + orderWithProducts.getId() + "/"
                    + clientAsExpeditor.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, String.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Utilizatorul nu are persimiunea de a executa aceasta operatiune!]");
        }
    }

    @Test
    public void deliver_whenHavingAnOrderWhichIsCanceled_shouldThrowAnException(){
        User expeditor = utilsComponent.saveUserWithRole(Roles.EXPEDITOR);
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("code1ForExpeditorForCanceledOrder", "code2ForExpeditorForCanceledOrder");

        Orders orderWithProducts = generateOrderItems(product, client);
        orderWithProducts.setCanceled(true);
        orderRepository.save(orderWithProducts);

        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/" + orderWithProducts.getId() + "/"
                    + expeditor.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, String.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Comanda a fost anulata!]");
        }
    }

    @Test
    public void cancel_whenValidOrder_shouldCancelIt(){
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("productForCancelOrder1", "productForCancelOrder2");

        Orders orderWithProducts = generateOrderItems(product, client);
        orderWithProducts.setCanceled(true);
        orderRepository.save(orderWithProducts);

        restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/cancel/" + orderWithProducts.getId() + "/"
                + client.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);

        Orders orderFromDB = orderRepository.findById(orderWithProducts.getId()).get();

        assertThat(orderFromDB.isCanceled()).isTrue();

    }

    @Test
    public void cancel_whenOrderIsAlreadySent_shouldThrowAnException(){
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("productForCancelSentOrder1", "productForCancelSentOrder2");

        Orders orderWithProducts = generateOrderItems(product, client);
        orderWithProducts.setCanceled(true);
        orderRepository.save(orderWithProducts);

        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/cancel/" + orderWithProducts.getId() + "/"
                    + client.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400: [Comanda a fost deja livrata!]");
        }

    }

    @Test
    public void cancel_whenUserIsAdmin_shouldThrowAnException(){
        User admin = utilsComponent.saveUserWithRole(Roles.ADMIN);
        Product product = utilsComponent.storeTwoProductsInDatabase("productForCancelAdminOrder1", "productForCancelAdminOrder2");

        Orders orderWithProducts = generateOrderItems(product, admin);
        orderRepository.save(orderWithProducts);

        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/cancel/" + orderWithProducts.getId() + "/"
                    + admin.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Utilizatorul nu are persimiunea de a executa aceasta operatiune!]");
        }

    }

    @Test
    public void cancel_whenUserIsAnExpeditor_shouldThrowAnException(){
        User expeditor = utilsComponent.saveUserWithRole(Roles.EXPEDITOR);
        Product product = utilsComponent.storeTwoProductsInDatabase("productForCancelExpeditorOrder1", "productForCancelExpeditorOrder2");

        Orders orderWithProducts = generateOrderItems(product, expeditor);

        orderRepository.save(orderWithProducts);

        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/cancel/" + orderWithProducts.getId() + "/"
                    + expeditor.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Utilizatorul nu are persimiunea de a executa aceasta operatiune!]");
        }
    }

    @Test
    @Transactional
    public void return_whenOrderValid_shouldReturnIt(){
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("productForReturn1", "productForReturn2");
        Orders orderWithProducts = utilsComponent.saveDeliveredOrder(client, product);

        restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/return/" + orderWithProducts.getId() + "/"
                + client.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);

        Orders orderFromDB = orderRepository.findById(orderWithProducts.getId()).get();

        assertThat(orderFromDB.isReturned()).isTrue();
        assertThat(orderFromDB.getOrderItems().get(0).getProduct().getStock()).isEqualTo(product.getStock()
        + orderWithProducts.getOrderItems().get(0).getQuantity());
    }

    @Test
    public void return_whenOrderIsNotDelivered_shouldThrowAnException(){
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("productNotDeliveredForReturn1", "productNotDeliveredForReturn2");
        Orders orderWithProducts = utilsComponent.saveOrder(client, product);

        try {
        restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/return/" + orderWithProducts.getId() + "/"
                + client.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Comanda nu poate fii returnata pentru ca nu a fost livrata!]");
        }
    }

    @Test
    public void return_whenOrderIsCanceled_shouldThrowAnException(){
        User client = utilsComponent.saveUserWithRole(Roles.CLIENT);
        Product product = utilsComponent.storeTwoProductsInDatabase("productCanceledForReturn1", "productCanceledForReturn2");
        Orders orderWithProducts = utilsComponent.saveCanceledAndDeliveredOrder(client, product);

        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/return/" + orderWithProducts.getId() + "/"
                    + client.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Comanda a fost anulata!]");
        }
    }

    @Test
    public void return_whenUserIsAdmin_shouldThrowAnException(){
        User adminAsClient = utilsComponent.saveUserWithRole(Roles.ADMIN);
        Product product = utilsComponent.storeTwoProductsInDatabase("productAdminForReturn1", "productAdminForReturn2");
        Orders orderWithProducts = utilsComponent.saveDeliveredOrder(adminAsClient, product);

        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/return/" + orderWithProducts.getId() + "/"
                    + adminAsClient.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Utilizatorul nu are persimiunea de a executa aceasta operatiune!]");
        }
    }

    @Test
    public void return_whenUserIsExpeditor_shouldThrowAnException(){
        User expeditorAsClient = utilsComponent.saveUserWithRole(Roles.EXPEDITOR);
        Product product = utilsComponent.storeTwoProductsInDatabase("productExpeditorForReturn1", "productExpeditorForReturn2");
        Orders orderWithProducts = utilsComponent.saveDeliveredOrder(expeditorAsClient, product);

        try {
            restTemplateForPatch.exchange(UtilsComponent.LOCALHOST + port + "/order/return/" + orderWithProducts.getId() + "/"
                    + expeditorAsClient.getId(), HttpMethod.PATCH, HttpEntity.EMPTY, Void.class);
        } catch (RestClientException exception) {
            assertThat(exception.getMessage()).isEqualTo("400 : [Utilizatorul nu are persimiunea de a executa aceasta operatiune!]");
        }
    }


    private OrderVO createOrderVO(User user, Product product) {
        OrderVO orderVO = new OrderVO();
        orderVO.setUserId((int) user.getId());
        Map<Integer, Integer> orderMap = new HashMap<>();
        orderMap.put((int) product.getId(), 1);
        orderVO.setProductsIdsToQuantity(orderMap);
        return orderVO;
    }

    private Orders generateOrderItems(Product product, User user) {
        Orders order = new Orders();
        order.setUser(user);
        List<OrderItem> orderItems = new ArrayList<>();
        OrderItem orderItem = generateOrderItem(product);
        orderItems.add(orderItem);
        order.setOrderItems(orderItems);
        return order;
    }

    private OrderItem generateOrderItem(Product product) {
        OrderItem orderItem = new OrderItem();
        orderItem.setQuantity(1);
        orderItem.setProduct(product);
        return orderItem;
    }

}