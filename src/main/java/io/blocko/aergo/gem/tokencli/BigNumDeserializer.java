package io.blocko.aergo.gem.tokencli;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;

public class BigNumDeserializer extends JsonDeserializer<BigNum> {
    @Override
    public BigNum deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        ObjectCodec objectCodec = p.getCodec();
        JsonNode jsonNode = objectCodec.readTree(p);
        JsonNode bigNode = jsonNode.get("_bignum");
        final String big = bigNode.textValue();
        if( big == null ) {
            throw new IOException("field _bignum is required.");
        }
        return new BigNum(big);
    }
}
