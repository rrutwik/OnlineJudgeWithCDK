package com.myorg;

import software.amazon.awscdk.core.Environment;

public class Utils {
    // Helper method to build an environment
    static private String accountNumber =  "940880199932";
    static private String region =  "us-east-1"; //default
    static public Environment makeEnv(String account, String region) {
        account = (account == null) ? System.getenv("CDK_DEPLOY_ACCOUNT") : account;
        region = (region == null) ? System.getenv("CDK_DEPLOY_REGION") : region;
        account = (account == null) ? System.getenv("CDK_DEFAULT_ACCOUNT") : account;
        region = (region == null) ? System.getenv("CDK_DEFAULT_REGION") : region;
        return Environment.builder()
                .account(account)
                .region(region)
                .build();
    }
    static public Environment makeEnv() {
        return makeEnv(accountNumber, region);
    }
}
