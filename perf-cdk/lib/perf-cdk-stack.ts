import * as cdk from '@aws-cdk/core';
import * as ec2 from '@aws-cdk/aws-ec2';
import * as iam from '@aws-cdk/aws-iam';
import {Effect, PolicyStatement} from '@aws-cdk/aws-iam';

export class PerfCdkStack extends cdk.Stack {
    constructor(scope: cdk.Construct, id: string, props?: cdk.StackProps) {
        super(scope, id, props);
        const defaultVpc = ec2.Vpc.fromLookup(this, 'VPC', {isDefault: true})
        const role = new iam.Role(
            this,
            'GJPerfTestEC2',
            {assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com')}
        )
        role.addToPolicy(new PolicyStatement({
            effect: Effect.ALLOW,
            actions: ["s3:PutObject",
                "s3:GetObject",
                "s3:ListBucket"
            ],
            resources: [
                "arn:aws:s3:::graphql-java-perf-tests",
                "arn:aws:s3:::graphql-java-perf-tests/*"]
        }));

        // lets create a security group for our instance
        // A security group acts as a virtual firewall for your instance to control inbound and outbound traffic.
        const securityGroup = new ec2.SecurityGroup(
            this,
            'simple-instance-1-sg',
            {
                vpc: defaultVpc,
                allowAllOutbound: true, // will let your instance send outboud traffic
                securityGroupName: 'simple-instance-1-sg',
            }
        )

        // lets use the security group to allow inbound traffic on specific ports
        securityGroup.addIngressRule(
            ec2.Peer.anyIpv4(),
            ec2.Port.tcp(22),
            'Allows SSH access from Internet'
        )

        // securityGroup.addIngressRule(
        //     ec2.Peer.anyIpv4(),
        //     ec2.Port.tcp(80),
        //     'Allows HTTP access from Internet'
        // )
        //
        // securityGroup.addIngressRule(
        //     ec2.Peer.anyIpv4(),
        //     ec2.Port.tcp(443),
        //     'Allows HTTPS access from Internet'
        // )

        // Finally lets provision our ec2 instance
        const instance = new ec2.Instance(this, 'simple-instance-1', {
            vpc: defaultVpc,
            role: role,
            securityGroup: securityGroup,
            instanceName: 'simple-instance-1',
            instanceType: ec2.InstanceType.of( // t2.micro has free tier usage in aws
                ec2.InstanceClass.T2,
                ec2.InstanceSize.MICRO
            ),
            machineImage: ec2.MachineImage.latestAmazonLinux({
                generation: ec2.AmazonLinuxGeneration.AMAZON_LINUX_2,
            }),

            keyName: 'simple-instance-1-key',
        })
        // instance.addUserData(
        //     fs.readFileSync('lib/setup.sh', 'utf8')
        // )

        new cdk.CfnOutput(this, 'simple-instance-1-output', {
            value: instance.instancePublicIp
        })

    }
}

