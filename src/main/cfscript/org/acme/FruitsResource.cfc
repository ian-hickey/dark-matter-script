/*
  This test component represents a basic cfscript component.
  This is an example REST service that gets and adds fruits.
*/
@Path("/fruits")
component {

    property name="fruits" type="Array";
    property name="aStruct" type="Struct";
    property name="aString" value="This is a string type.";
    property name="aMailer" type="Mailer" hint="This is a Mailer component type.";
    property name="pi" type="numeric" value="3.1459" hint="This ends up being a double";
    property name="aInt" value="227" hint="This ends up being an int";
    property name="aBool" value="true" hint="This ends up being boolean";
    property name="aBoolStr" type="string" value="true" hint="String because the type overrides the inferred type";

    public function init() {
        arrayAppend(fruits, new org.acme.model.Fruit("Apple", "Winter fruit"));
        arrayAppend(fruits, new org.acme.model.Fruit("Pineapple", "Tropical fruit"));
    }

    @GET
    public Response function list() {
        Response.ok(fruits).build();
    }

    @POST
    Response function add(any fruit) {
        arrayAppend(fruits, fruit);
        return Response.ok(fruits).build();
    }
}