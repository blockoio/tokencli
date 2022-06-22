package io.blocko.aergo.gem.tokencli;

import hera.api.model.*;
import hera.client.AergoClient;
import hera.client.AergoClientBuilder;
import hera.exception.HerajException;
import hera.key.AergoKey;
import hera.model.KeyAlias;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

/**
 * tokencli {networkName} {tokenAddress} {command} [[args] [for] [command]]
 */
@Slf4j
public class TokenCliApp {
    // 설정항목들
    private static final String walletAddr = "AmNQ6skDfZEF6F47eoMYdFPvupyZcGWag2D1jHxkW9iPdiaAv4LB";
    private static final String walletPK = "47YaDMC5z7KGvFFpfJk5wDusCQaqpEy6PMR9TyseM58tg3stMf11DbbPVHcPbxrzJ44Qp2mNj";
    private static final String walletPass = "1234";

    public static Map<String, String> networkUrlMap;
    public static Map<String, String> scanUrlMap;

    static {
        networkUrlMap = new HashMap<>();
        networkUrlMap.put("main", "mainnet-api.aergo.io:7845");
        networkUrlMap.put("test", "testnet-api.aergo.io:7845");
//        networkUrlMap.put("alpha", "alpha-api.aergo.io:7845");
        scanUrlMap = new HashMap<>();
        scanUrlMap.put("main", "https://api.aergoscan.io/mainnet/v2");
        scanUrlMap.put("test", "https://api.aergoscan.io/testnet/v2");
        // aergoscan 2.0 for alphanet is not available yet.
//        scanUrlMap.put("alpha", "https://api.aergoscan.io/alphanet/v2");
    }

    public enum CMD {
        HISTORY,
        BALANCE,
        TRANSFER
    }

    public interface Commander {
        String name();

        int argCount();

        void execCmd(String networkName, String tokenAddress, String... args);
    }

    public static Map<CMD, Commander> cmdMap;

    static {
        cmdMap = new HashMap<>();
        cmdMap.put(CMD.HISTORY, new HistoryCommander());
        cmdMap.put(CMD.TRANSFER, new TransferCommander());
        cmdMap.put(CMD.BALANCE, new BalanceCommander());
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        if (args.length < 3) {
            System.err.println("Usage: tokencli <network> <tokenAddress> <command> [args]");
            System.exit(1);
        }

        String networkName = args[0];
        String apiUrl = getRPCUrl(networkName);
        String tokenAddress = args[1];
        CMD cmd = CMD.valueOf(args[2].toUpperCase(Locale.ROOT));
        Commander commander = cmdMap.get(cmd);
        String[] cmdArgs = Arrays.copyOfRange(args, 3, args.length);
        if (cmdArgs.length < commander.argCount()) {
            System.err.printf("Usage: tokencli <%s> %s <%d args>", commander.name(), commander.argCount());
            System.exit(1);
        }
        commander.execCmd(networkName, tokenAddress, cmdArgs);
    }

    private static String getRPCUrl(String network) throws IllegalArgumentException {
        String apiUrl = networkUrlMap.get(network);
        if (apiUrl == null) {
            throw new IllegalArgumentException("invalid network");
        }
        return apiUrl;
    }
    private static String getAergoscanUrl(String network) throws IllegalArgumentException {
        String apiUrl = scanUrlMap.get(network);
        if (apiUrl == null) {
            throw new IllegalArgumentException("invalid network");
        }
        return apiUrl;
    }
    private static String getHistoryUrl(String network) throws  IllegalArgumentException {
        return getAergoscanUrl(network)+"/tokenTransfers";
    }

    public static AergoClient createConn(String endpoint) {
        AergoClient aergoClient = new AergoClientBuilder()
                .withEndpoint(endpoint)
                .withNonBlockingConnect()
                .build();
        return aergoClient;
    }

    @RequiredArgsConstructor
    static abstract class BaseCommander implements Commander {
        final String name;
        final int argCount;

        @Override
        public int argCount() {
            return argCount;
        }

        @Override
        public String name() {
            return name;
        }
    }

    /**
     * history {walletAddress}
     */
    static class HistoryCommander extends BaseCommander implements Commander {
        public HistoryCommander() {
            super(CMD.HISTORY.name().toLowerCase(), 0);
        }

        @Override
        public void execCmd(String networkName, String contract
                , String... args) {

            OkHttpClient client = new OkHttpClient();
            String walletAddress = args[0];

            HttpUrl.Builder httpBuilder = HttpUrl.parse(getHistoryUrl(networkName)).newBuilder();
            httpBuilder.addEncodedQueryParameter("q",String.format("(to:%s+OR+from:%s)+AND+NOT+(address:%s)",walletAddress,walletAddress,walletAddress));

            String result = null;
            try {
                Request request = new Request.Builder().url(httpBuilder.build()).build();
                log.debug("request query is {}",request.url().uri().toString());
                result = run(client, request);
                System.out.println("history result");
                System.out.printf("%s\n",result);
            } catch (IOException e) {
                System.err.println("failed to get history");
            }
        }

        String run (OkHttpClient client, Request request) throws IOException {
            try (Response response = client.newCall(request).execute()) {
                return response.body().string();
            }
        }

    }

    /**
     * transfer {toWalletAddress} {amount}
     */
    static class TransferCommander extends BaseCommander implements Commander {
        public TransferCommander() {
            super(CMD.TRANSFER.name().toLowerCase(), 2);
        }

        @Override
        public void execCmd(String networkName, String contract
                , String... args) {
            String receiverAddress = getRPCUrl(args[0]);
            String amount = args[1];
            AergoClient client = createConn(networkName);
            ContractAddress contractAddress = ContractAddress.of(contract);
            ContractInterface abi = client.getContractOperation().
                    getContractInterface(contractAddress);
            TokenWallet wallet = new TokenWallet(initPikkVoterKeys(), client, abi);
            String txHash = wallet.tranfer(receiverAddress, amount);
            System.out.printf("transfer from %s to %s was committed : hash %s\n", wallet.getSignerAddress(), receiverAddress, txHash);
        }
    }

    /**
     * check balance
     *
     * balance {walletAddress}
     */
    static class BalanceCommander extends BaseCommander implements Commander {
        public BalanceCommander() {
            super(CMD.BALANCE.name().toLowerCase(), 0);
        }

        @Override
        public void execCmd(String networkName, String contract
                , String... args) {
            AergoClient client = createConn(getRPCUrl(networkName));
            ContractAddress contractAddress = ContractAddress.of(contract);
            ContractInterface abi = client.getContractOperation().
                    getContractInterface(contractAddress);
            String walletAddress = args[0];
            String balance = getBalance(AccountAddress.of(walletAddress), client, abi);
            System.out.printf("balance of wallet %s is %s\n", walletAddress, balance);
        }

        synchronized public String getBalance(AccountAddress walletAddress, AergoClient aergoClient, ContractInterface abi) {
            ContractInvocation query = abi.newInvocationBuilder()
                    .function("balanceOf")
                    .args(walletAddress.getEncoded())
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


    private static AergoKey initPikkVoterKeys() {
        log.debug("Loading private key of addr ", walletAddr);
        AergoKey voterKey = loadSigner(walletPK, walletPass);
        return voterKey;
    }

    private static AergoKey loadSigner(String privateKey, String password) {
        // in-memory keystore is used
        AergoKey aergoKey;
        try {
            aergoKey = AergoKey.of(privateKey, password);
        } catch (HerajException e) {
            throw new IllegalArgumentException("invalid aergokey password");
        }
        Authentication authentication = Authentication.of(KeyAlias.of(aergoKey.getAddress().getEncoded()), password);
        return aergoKey;
    }

}
