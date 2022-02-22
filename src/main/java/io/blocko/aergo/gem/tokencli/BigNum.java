package io.blocko.aergo.gem.tokencli;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.math.BigInteger;

/**
 * aergo smartcontract의 bignum 타입에 대응한 구조체. json encoding을 위한 처리방식이다.
 */
@JsonSerialize(using = BigNumSerializer.class)
@JsonDeserialize(using = BigNumDeserializer.class)
public class BigNum {
    private final BigInteger number;

    public BigNum(String big) {
        this(new BigInteger(big));
    }

    public BigNum(BigInteger number) {
        this.number = number;
    }

    @Override
    public String toString() {
        return number.toString();
    }

}
