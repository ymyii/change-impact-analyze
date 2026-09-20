> 适用于 `wiki/glossary/<term>.md`，并使用 `type: glossary`。


---
name: "<Canonical English Term>"
type: glossary
---

## Definition

<使用简短中文定义该术语在当前项目中的准确含义。>

## Usage

<说明术语适用的系统语境，以及该含义对理解架构或修改代码的作用。>

> 有可靠证据时，依次增加：

```markdown
## Aliases

- `<项目实际使用的简称或同义表达>`

## Distinctions

- `<容易混淆的术语>`：<说明两者的关键区别。>
```

> **填写说明**
>
> - `Definition` 只说明术语是什么，不解释完整机制、操作步骤、决策或约束。
> - `Usage` 说明项目语境和理解价值，不维护使用页面清单。
> - `Aliases` 只记录可靠来源中实际使用的简称或同义表达。
> - `Distinctions` 只区分容易产生误解的近义词、同义词或同名异义词。
> - 正文在每个页面首次使用该术语时通过相对 Markdown 链接引用本页，后续出现不重复链接。
> - 包含 Glossary Markdown 链接的句子在不打开本页时仍需表达基本含义。
> - Glossary 页面不使用 `children` 或 `relations`。
> - 没有可靠内容时直接省略 `Aliases` 或 `Distinctions`。
>
> 删除所有占位符和不适用的示例项。
