<?php

declare(strict_types=1);

namespace PhpStanTypeDoc\Conformance;

use PhpParser\Node;
use PhpParser\Node\Expr\Assign;
use PhpParser\Node\Expr\Variable;
use PHPStan\Analyser\Scope;
use PHPStan\Rules\Rule;
use PHPStan\Rules\RuleErrorBuilder;
use PHPStan\Type\VerbosityLevel;

/**
 * すべての変数代入について、PHPStan が推論した型を「エラー」として報告する。
 *
 * ソースを一切変更せずに、プロジェクト全体の変数の正解型を採るための計測用ルール。
 * 出力は extract.py がパースして JSON にする。
 *
 * @implements Rule<Assign>
 */
final class DumpAssignRule implements Rule
{
    public function getNodeType(): string
    {
        return Assign::class;
    }

    public function processNode(Node $node, Scope $scope): array
    {
        if (!$node->var instanceof Variable || !is_string($node->var->name)) {
            return [];
        }

        $type = $scope->getType($node->expr);

        return [
            RuleErrorBuilder::message(sprintf(
                'VARTYPE|%s|%s',
                $node->var->name,
                $type->describe(VerbosityLevel::precise()),
            ))->identifier('conformance.varType')->build(),
        ];
    }
}
