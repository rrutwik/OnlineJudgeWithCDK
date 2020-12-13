package com.myorg;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.NetworkLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.NetworkLoadBalancedFargateServiceProps;
import software.amazon.awscdk.services.ecs.patterns.NetworkLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.NetworkLoadBalancerProps;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.ARecordProps;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneAttributes;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketProps;

@Getter
class OnlineJudgeStackProps implements StackProps {

  private final Cluster cluster;
  private final String dbHost;
  private final String dbPort;
  private final String rdHost;
  private final String rdPort;
  private final Environment env;

  public OnlineJudgeStackProps(final Environment env, final Cluster cluster, final String dbHost,
      final String dbPort,
      final String rdHost, final String rdPort) {
    this.cluster = cluster;
    this.dbHost = dbHost;
    this.dbPort = dbPort;
    this.rdHost = rdHost;
    this.rdPort = rdPort;
    this.env = env;
  }
}

public class OnlineJudgeStack extends Stack {

  private final Cluster cluster;

  public OnlineJudgeStack(final Construct scope, final String id,
      final OnlineJudgeStackProps props) {
    super(scope, id, props);
    this.cluster = props.getCluster();
    final Bucket testCaseBucket = new Bucket(this, "s3Bucket", BucketProps.builder().build());
    final NetworkLoadBalancedFargateService onlinejudge = getOnlineJudge(props.getCluster(),
        props.getDbHost(), props.getDbPort(), props.getRdHost(),
        props.getRdPort(), testCaseBucket.getBucketName());
    onlinejudge.getService().getConnections().allowFromAnyIpv4(Port.allTraffic());
    final NetworkLoadBalancedFargateService judgerserver = getJudgeServer(props.getCluster(),
        onlinejudge.getLoadBalancer().getLoadBalancerDnsName(),
        "80", testCaseBucket.getBucketName());
    judgerserver.getService().getConnections().allowFromAnyIpv4(Port.allTraffic());
    testCaseBucket.grantReadWrite(judgerserver.getService().getTaskDefinition().getTaskRole());
    testCaseBucket.grantReadWrite(onlinejudge.getService().getTaskDefinition().getTaskRole());
//    final ScalableTaskCount autoscaleTaskCount = onlinejudge.getService()
//        .autoScaleTaskCount(EnableScalingProps.builder().maxCapacity(5).minCapacity(2).build());
//    autoscaleTaskCount.scaleOnCpuUtilization("OnlineJudgeCpuAutoScale",
//        CpuUtilizationScalingProps
//            .builder()
//            .scaleInCooldown(Duration.minutes(1))
//            .scaleOutCooldown(Duration.minutes(1))
//            .targetUtilizationPercent(60)
//            .build());
//    autoscaleTaskCount.scaleOnMemoryUtilization("OnlineJudgeMemoryAutoScale",
//        MemoryUtilizationScalingProps
//            .builder()
//            .scaleInCooldown(Duration.minutes(1))
//            .scaleOutCooldown(Duration.minutes(1))
//            .targetUtilizationPercent(60)
//            .build());
//    ScalableTaskCount scalableTaskCount = new ScalableTaskCount(this, "scalableTaskCount",
//        ScalableTaskCountProps
//            .builder()
//            .
//        .build());
    // The code that defines your stack goes here
    // Friendly Name To Access website
    final IHostedZone hostedZone = HostedZone.fromHostedZoneAttributes(this, "MyZone",
        HostedZoneAttributes
            .builder()
            .hostedZoneId("Z06547642RTPEM6E1MBQ3")
            .zoneName("aws.rutwikpatel.com")
            .build());
    new ARecord(this, "nameofOj", ARecordProps.builder()
        .zone(hostedZone)
        .target(RecordTarget.fromAlias(new LoadBalancerTarget(onlinejudge.getLoadBalancer())))
        .recordName("oj.aws.rutwikpatel.com")
        .comment("Link to OJ")
        .build());
  }

  private NetworkLoadBalancedFargateService getOnlineJudge(final Cluster cluster,
      final String dbHost,
      final String dbPort,
      final String rdHost,
      final String rdPort,
      final String bucketName) {
    final NetworkLoadBalancer networkLoadBalancer = new NetworkLoadBalancer(this,
        "onlineJudgeLoadBalancer",
        NetworkLoadBalancerProps.builder()
            .vpc(this.cluster.getVpc())
            .crossZoneEnabled(true)
            .internetFacing(true)
            .build());
    final NetworkLoadBalancedTaskImageOptions networkLoadBalancedTaskImageOptions = NetworkLoadBalancedTaskImageOptions
        .builder()
        .containerName("TestContainer")
        .containerPort(8000)
        .environment(
            getEnvironmentVariable("onlinejudge", "onlinejudge", "onlinejudge", dbHost, dbPort,
                rdHost, rdPort, bucketName))
        .image(ContainerImage.fromAsset("../OJ/OnlineJudge"))
        .family("OJ")
        .build();
    final NetworkLoadBalancedFargateServiceProps networkLoadBalancedFargateServiceProps = NetworkLoadBalancedFargateServiceProps
        .builder()
        .cpu(256)
        .loadBalancer(networkLoadBalancer)
        .memoryLimitMiB(512)
        .taskImageOptions(networkLoadBalancedTaskImageOptions)
        .cluster(cluster)
        .desiredCount(1)
        .build();
    return new NetworkLoadBalancedFargateService(this, "thisOnlineJudge",
        networkLoadBalancedFargateServiceProps);
  }

  private Map<String, String> getEnvironmentVariable(final String dbName, final String userName,
      final String password, final String dbHost, final String dbPort, final String rdHost,
      final String rdPort, final String bucketName) {
    final HashMap<String, String> env = new HashMap<String, String>();
    env.put("POSTGRES_DB", dbName);
    env.put("POSTGRES_USER", userName);
    env.put("POSTGRES_PASSWORD", password);
    env.put("POSTGRES_HOST", dbHost);
    env.put("POSTGRES_PORT", dbPort);
    env.put("REDIS_HOST", rdHost);
    env.put("REDIS_PORT", rdPort);
    env.put("JUDGE_SERVER_TOKEN", "CHANGE_THIS");
    env.put("TEST_CASE_BUCKET", bucketName);
    return env;
  }

  private Map<String, String> getEnvironmentVariable(final String backendHost,
      final String backendPort,
      final String judgeServerHost, final String judgeServerPort, final String bucketName) {
    final HashMap<String, String> env = new HashMap<String, String>();
    env.put("SERVICE_HOST", judgeServerHost);
    env.put("SERVICE_PORT", judgeServerPort);
    env.put("BACKEND_HOST", backendHost);
    env.put("BACKEND_PORT", backendPort);
    env.put("TOKEN", "CHANGE_THIS");
    env.put("TEST_CASE_BUCKET", bucketName);
    return env;
  }

  private NetworkLoadBalancedFargateService getJudgeServer(final Cluster cluster,
      final String backendHost, final String backendPort, final String bucketName) {
    final NetworkLoadBalancer networkLoadBalancer = new NetworkLoadBalancer(this,
        "judgeserverLoadBalancer",
        NetworkLoadBalancerProps.builder()
            .vpc(this.cluster.getVpc())
            .crossZoneEnabled(true)
            .build());
    final NetworkLoadBalancedTaskImageOptions networkLoadBalancedTaskImageOptions = NetworkLoadBalancedTaskImageOptions
        .builder()
        .containerName("TestContainer")
        .containerPort(8080)
        .family("OJ")
        .environment(
            getEnvironmentVariable(backendHost, backendPort,
                networkLoadBalancer.getLoadBalancerDnsName(), "80", bucketName))
        .image(ContainerImage.fromAsset("../OJ/JudgeServer"))
        .build();
    final NetworkLoadBalancedFargateServiceProps networkLoadBalancedFargateServiceProps = NetworkLoadBalancedFargateServiceProps
        .builder()
        .loadBalancer(networkLoadBalancer)
        .cpu(256)
        .memoryLimitMiB(512)
        .taskImageOptions(networkLoadBalancedTaskImageOptions)
        .cluster(cluster)
        .publicLoadBalancer(false)
        .desiredCount(1)
        .build();
    return new NetworkLoadBalancedFargateService(this, "judgeServer",
        networkLoadBalancedFargateServiceProps);
  }
}
