package io.blocko.aergo.gem.tokencli;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class BigNumSerializer extends JsonSerializer<BigNum> {

    @Override
    public void serialize(BigNum value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeObjectField("_bignum",value.toString());
        gen.writeEndObject();
    }

    static class BigNumNotation {
        final String _bigNum;

        BigNumNotation(String bigNum) {
            _bigNum = bigNum;
        }
    }
}
