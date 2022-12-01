package com.abc.senki.controller.client;

import com.abc.senki.handler.AuthenticationHandler;
import com.abc.senki.model.entity.*;
import com.abc.senki.model.payload.request.OrderRequest.AddOrderRequest;
import com.abc.senki.model.payload.request.OrderRequest.CartItemList;
import com.abc.senki.model.payload.response.ErrorResponse;
import com.abc.senki.model.payload.response.SuccessResponse;
import com.abc.senki.service.OrderService;
import com.abc.senki.service.PaypalService;
import com.abc.senki.service.UserService;
import com.paypal.api.payments.Order;
import com.paypal.api.payments.Payment;
import com.paypal.base.rest.PayPalRESTException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.apache.coyote.Response;
import org.hibernate.engine.jdbc.spi.SqlExceptionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

import com.abc.senki.common.OrderStatus.*;

import static com.abc.senki.common.OrderStatus.*;

@RestController
@RequestMapping("/api/order")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    @Autowired
    AuthenticationHandler authenticationHandler;

    public static final String SUCCESS_URL = "/api/order/pay/success";
    public static final String CANCEL_URL = "/api/order/pay/cancel";


    @Autowired
    UserService userService;
    @Autowired
    PaypalService paypalService;
    @Autowired
    OrderService orderService;

    @PostMapping("paypal")
    @Operation(summary = "Add PAYPAL order")
    public ResponseEntity<Object> addPayPalOrder(HttpServletRequest request,@RequestBody List<CartItemList> cartList){
        try{
            UserEntity user=authenticationHandler.userAuthenticate(request);
            if(user==null){
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("User not found", HttpStatus.BAD_REQUEST.value()));
            }

            OrderEntity order=new OrderEntity(user);
            //Set order address
            order.setAddress(user.getAddress());

            if(order.getAddress()==null){
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Address not found", HttpStatus.BAD_REQUEST.value()));
            }
            //Calculate total
            cartProcess(order,cartList);
            //Process Payment
            String link=paypalService.paypalPayment(order,request);
            if(link==null){
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Paypal payment error", HttpStatus.BAD_REQUEST.value()));
            }
            //Save order
            order.setMethod("PAYPAL");
            order.setStatus(PENDING.getMesssage());
            orderService.saveOrder(order);
            //Response
            HashMap<String,Object> data=new HashMap<>();
            data.put("link",link);
            return ResponseEntity
                    .ok(new SuccessResponse(HttpStatus.OK.value(),"PAYPAL Payment",data));
            
        }
        catch (Exception e){
            return ResponseEntity.status(400).body(new ErrorResponse("Out of stock",HttpStatus.BAD_REQUEST.value()));
        }
    }
    @PostMapping("cod")
    @Operation(summary = "Add COD order")
    public ResponseEntity<Object> addCODOrder(HttpServletRequest request,
                                              @RequestBody List<CartItemList> cartList
    ) {
        try{
            UserEntity user=authenticationHandler.userAuthenticate(request);
            if(user==null){
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("User not found", HttpStatus.BAD_REQUEST.value()));
            }

            OrderEntity order=new OrderEntity(user);
            //Set order address
            order.setAddress(user.getAddress());

            if(order.getAddress()==null){
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Address not found", HttpStatus.BAD_REQUEST.value()));
            }
            //Calculate total
            cartProcess(order,cartList);
            //Save order
            order.setMethod("COD");
            order.setStatus(PROCESSING.getMesssage());
            orderService.saveOrder(order);
            return ResponseEntity
                    .ok(new SuccessResponse(HttpStatus.OK.value(),"Order successfully",null));

        }
        catch (Exception e){
            return ResponseEntity.status(400).body(new ErrorResponse("Out of stock",HttpStatus.BAD_REQUEST.value()));
        }
    }

    @GetMapping("/pay/success/{id}")
    @Operation(summary = "Paypal payment success")
    public ResponseEntity<Object> successPay(@PathVariable String id,
                                             @RequestParam("paymentId") String paymentId,
                                             @RequestParam("PayerID") String payerId,
                                             HttpServletResponse response){
        //Execute payment
        try{
            Payment payment=paypalService.executePayment(paymentId,payerId);
            if(payment.getState().equals("approved")){
                Map<String,Object> data=new HashMap<>();
                orderService.updateOrderStatus(UUID.fromString(id),PROCESSING.getMesssage());
                //Process order if payment success
                data.put("orderId",id);
                response.sendRedirect("http://localhost:3000/paypal/success?orderId="+id);
            }
        }
        catch (PayPalRESTException e){
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage(),HttpStatus.BAD_REQUEST.value()));
        }
        catch (IOException e1){
            return ResponseEntity.badRequest().body(new ErrorResponse(e1.getMessage(),HttpStatus.BAD_REQUEST.value()));
        }
        return ResponseEntity.badRequest().body(new ErrorResponse("Payment failed",HttpStatus.BAD_REQUEST.value()));
    }
    @GetMapping("/pay/cancel/{id}")
    @Operation(summary = "Paypal payment cancel")
    public ResponseEntity<Object> cancelPay(@PathVariable String id){
        orderService.updateOrderStatus(UUID.fromString(id),CANCELLED.getMesssage());
        return ResponseEntity.badRequest().body(new ErrorResponse("Payment cancel",HttpStatus.BAD_REQUEST.value()));
    }

    //Process COD order
    public void cartProcess(OrderEntity order, List<CartItemList> listCart){
        List<OrderDetailEntity> orderDetailList = new ArrayList<>();
        for (CartItemList cartItem:listCart)
        {
            OrderDetailEntity orderDetail=new OrderDetailEntity();
            orderDetail.setInfo(order,
                    cartItem.getProductName(),
                    cartItem.getProductId(),
                    cartItem.getProductImage(),
                    cartItem.getQuantity(),
                    cartItem.getPrice());
            orderDetailList.add(orderDetail);
            order.setTotal(order.getTotal()+cartItem.getPrice()*cartItem.getQuantity());
        }
        //Set item and delete cart
        order.setOrderDetails(orderDetailList);
        order.setTotal(order.getTotal()+order.getShipFee());
    }



}