import {expect as expectCDK, MatchStyle, matchTemplate} from '@aws-cdk/assert';
import * as cdk from '@aws-cdk/core';
import * as PerfCdk from '../lib/perf-cdk-stack';

test('Empty Stack', () => {
    const app = new cdk.App();
    // WHEN
    const stack = new PerfCdk.PerfCdkStack(app, 'MyTestStack');
    // THEN
    expectCDK(stack).to(matchTemplate({
        "Resources": {}
    }, MatchStyle.EXACT))
});
