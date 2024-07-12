package customer.api;

import customer.domain.Address;
import customer.domain.Customer;
import akka.platform.javasdk.testkit.KeyValueEntityResult;
import akka.platform.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomerEntityTest {

  private Address address = new Address("Acme Street", "Acme City");
  private Customer customer = new Customer("id", "info@acme.com", "Acme Inc.", address);

  @Test
  public void testCustomerNameChange() {

    KeyValueEntityTestKit<Customer, CustomerEntity> testKit = KeyValueEntityTestKit.of(CustomerEntity::new);
    {
      KeyValueEntityResult<CustomerEntity.Ok> result = testKit.call(e -> e.create(customer));
      assertEquals(CustomerEntity.Ok.instance, result.getReply());
    }

    {
      KeyValueEntityResult<CustomerEntity.Ok> result = testKit.call(e -> e.changeName("FooBar"));
      assertEquals(CustomerEntity.Ok.instance, result.getReply());
      assertEquals("FooBar", testKit.getState().name());
    }

  }

  @Test
  public void testCustomerAddressChange() {

    KeyValueEntityTestKit<Customer, CustomerEntity> testKit = KeyValueEntityTestKit.of(CustomerEntity::new);
    {
      KeyValueEntityResult<CustomerEntity.Ok> result = testKit.call(e -> e.create(customer));
      assertEquals(CustomerEntity.Ok.instance, result.getReply());
    }

    {
      Address newAddress = new Address("Sesame Street", "Sesame City");
      KeyValueEntityResult<CustomerEntity.Ok> result = testKit.call(e -> e.changeAddress(newAddress));
      assertEquals(CustomerEntity.Ok.instance, result.getReply());
      assertEquals("Sesame Street", testKit.getState().address().street());
      assertEquals("Sesame City", testKit.getState().address().city());
    }

  }
}
