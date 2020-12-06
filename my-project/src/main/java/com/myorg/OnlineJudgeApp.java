package com.myorg;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class OnlineJudgeApp {

  public static void main(final String[] args) {
    final App app = new App();
    final Environment envUSAEast = Utils.makeEnv();
    final VpcStack vpcStack = new VpcStack(app, "vpcStack",
        StackProps.builder().env(envUSAEast).build());
    final OnlineJudgeStackProps stackProps = new OnlineJudgeStackProps(envUSAEast,
        vpcStack.getCluster(),
        vpcStack.getDbHost(),
        vpcStack.getDbPort(),
        vpcStack.getRdHost(),
        vpcStack.getRdPort());
    new OnlineJudgeStack(app, "OnlineJudgeStack", stackProps);
    app.synth();
  }
}