package io.blocko.aergo.gem.tokencli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import hera.api.ContractOperation;
import hera.api.model.*;
import hera.api.transaction.NonceProvider;
import hera.api.transaction.SimpleNonceProvider;
import hera.client.AergoClient;
import hera.key.Signer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;

/**
 * 체인에 토큰을 넣는 지갑 인터페이스.
 * 쓰레드 안정성을 보장하지 않는다.
 */
@Slf4j
public class TokenWallet {
    private static final ObjectMapper json = JsonMapper.builder() // or different mapper for other format
                .addModule(new ParameterNamesModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                // and possibly other configuration, modules, then:
                .build();

    public static final String QUERY_GET_VOTE = "getVote";

    private final Signer signer;
    private final AergoClient aergoClient;
    private final ContractInterface abi;
    private NonceProvider nonceProvider;

    public TokenWallet(Signer signer, AergoClient aergoClient, ContractInterface abi) {
        this.signer = signer;
        this.aergoClient = aergoClient;
        this.abi = abi;
        init();
    }

    void init() {
        log.info("initializing pikkle wallet with signer address {} and aergoClient {}",
                signer.getPrincipal().toString(), aergoClient);
        // nonce provider 준비
        nonceProvider = new SimpleNonceProvider();
        AccountState state = aergoClient.getAccountOperation().getState(signer.getPrincipal());
        nonceProvider.bindNonce(state);
    }

    public String getSignerAddress() {
        return signer.getPrincipal().getEncoded();
    }

    public void resetNonce() {
        AccountAddress address = signer.getPrincipal();
        long usedNonce = nonceProvider.getLastUsedNonce(address);
        // 예외 발생 등으로 nonce만 올라간 상황 등에서 이전 nonce로 되돌리기 위해 nonce를 받아서 새로 올린다.
        AccountState state = aergoClient.getAccountOperation().getState(address);
        nonceProvider.bindNonce(state);
        long newNonce = nonceProvider.getLastUsedNonce(address);
        log.info("resetting nonce of address {} : current nonce is {} and the new nonce is {}"
                , address.getEncoded(), usedNonce, newNonce);
    }

    public String tranfer(String receiverAddress, String amount) {
        ContractInvocation invocation = abi.newInvocationBuilder()
                .function("transfer").delegateFee(false)
                .args(receiverAddress, amount)
                .build();
        String txHash = executeContractTx(invocation);
        return txHash;
    }

    private String executeContractTx(ContractInvocation invocation) {
        long nonce = nonceProvider.incrementAndGetNonce(signer.getPrincipal());
        log.trace("invocation signer {}, nonce {} : args {}", signer, nonce,  invocation.getArgs());
        try {
            ContractOperation operation = aergoClient.getContractOperation();
            TxHash txHash = operation
                    .executeTx(signer, invocation, nonce, Fee.ZERO);
            log.debug("Execute {} tx hash: {}", invocation.getArgs().get(0), txHash);
            return txHash.getEncoded();
        } catch (RuntimeException e) {
            log.warn("Failed to execute {} : {}", invocation.getArgs().get(0), e.getMessage());
            throw e;
        }
    }

    synchronized public String getBalance() {
        ContractInvocation query = abi.newInvocationBuilder()
                .function("balanceOf")
                .args(signer.getPrincipal().getEncoded())
                .build();
        ContractResult result = aergoClient.getContractOperation().query(query);
        log.debug("Query {} result: {}",query.getArgs().get(0),result);
        try {
            BigNum balance = result.bind(BigNum.class);
            return balance.toString();
        } catch (IOException e) {
            throw new RuntimeException("failed to bind result ", e);
        }
    }
}
