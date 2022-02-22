package io.blocko.aergo.gem.tokencli;

import hera.api.model.Authentication;
import hera.api.model.ContractAddress;
import hera.api.model.ContractInterface;
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

@Slf4j
public class TokenCliApp {
    // 설정항목들
    private static final String walletAddr = "AmNQ6skDfZEF6F47eoMYdFPvupyZcGWag2D1jHxkW9iPdiaAv4LB";
    private static final String walletPK = "47YaDMC5z7KGvFFpfJk5wDusCQaqpEy6PMR9TyseM58tg3stMf11DbbPVHcPbxrzJ44Qp2mNj";
    private static final String walletPass = "1234";
    private static final String historyUrl = "http://218.147.120.149:27876/testnet/tokenTransfers";

    public static Map<String, String> networkUrlMap;

    static {
        networkUrlMap = new HashMap<>();
        networkUrlMap.put("main", "mainnet-api.aergo.io:7845");
        networkUrlMap.put("test", "testnet-api.aergo.io:7845");
        networkUrlMap.put("alpha", "alpha-api.aergo.io:7845");
    }

    public enum CMD {
        HISTORY,
        BALANCE,
        TRANSFER
    }

    public interface Commander {
        String name();

        int argCount();

        void execCmd(String apiUrl, String tokenAddress, String... args);
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
            System.err.println("Usage: tokencli <network> <command> [args]");
            System.exit(1);
        }

        String apiUrl = networkUrlMap.get(args[0]);
        if (apiUrl == null) {
            apiUrl = args[0];
        }
        String tokenAddress = args[1];
        CMD cmd = CMD.valueOf(args[2].toUpperCase(Locale.ROOT));
        Commander commander = cmdMap.get(cmd);
        String[] cmdArgs = Arrays.copyOfRange(args, 3, args.length);
        if (cmdArgs.length < commander.argCount()) {
            System.err.printf("Usage: tokencli <%s> %s <%d args>", commander.name(), commander.argCount());
            System.exit(1);
        }
        commander.execCmd(apiUrl, tokenAddress, cmdArgs);
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

    static class HistoryCommander extends BaseCommander implements Commander {
        public HistoryCommander() {
            super(CMD.HISTORY.name().toLowerCase(), 0);
        }

        @Override
        public void execCmd(String apiUrl, String contract
                , String... args) {

            OkHttpClient client = new OkHttpClient();
            AergoKey aergoKey = initPikkVoterKeys();
            String walletAddress = aergoKey.getAddress().getEncoded();

            HttpUrl.Builder httpBuilder = HttpUrl.parse(historyUrl).newBuilder();
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

    static class TransferCommander extends BaseCommander implements Commander {
        public TransferCommander() {
            super(CMD.TRANSFER.name().toLowerCase(), 2);
        }

        @Override
        public void execCmd(String apiUrl, String contract
                , String... args) {
            String receiverAddress = args[0];
            String amount = args[1];
            AergoClient client = createConn(apiUrl);
            ContractAddress contractAddress = ContractAddress.of(contract);
            ContractInterface abi = client.getContractOperation().
                    getContractInterface(contractAddress);
            TokenWallet wallet = new TokenWallet(initPikkVoterKeys(), client, abi);
            String txHash = wallet.tranfer(receiverAddress, amount);
            System.out.printf("transfer from %s to %s was commited : hash %s\n", wallet.getSignerAddress(), receiverAddress, txHash);
        }
    }

    static class BalanceCommander extends BaseCommander implements Commander {
        public BalanceCommander() {
            super(CMD.BALANCE.name().toLowerCase(), 0);
        }

        @Override
        public void execCmd(String apiUrl, String contract
                , String... args) {
            AergoClient client = createConn(apiUrl);
            ContractAddress contractAddress = ContractAddress.of(contract);
            ContractInterface abi = client.getContractOperation().
                    getContractInterface(contractAddress);
            TokenWallet wallet = new TokenWallet(initPikkVoterKeys(), client, abi);
            String balance = wallet.getBalance();
            System.out.printf("balance of wallet %s is %s\n", wallet.getSignerAddress(), balance);
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
