# Examples

## Abbreviations

**Example**  
The HTML specification is maintained by the W3C.
*[HTML]: Hyper Text Markup Language
*[W3C]: World Wide Web Consortium

## Snippets

> When Snippets is enabled, content from other files can be embedded, which is especially useful to include abbreviations from a central file – a glossary – and embed them into any other file.

**Example**  
Oblx biedt support for DCAT in de toekomst. 

--8<-- "snippets/glossary.md"

## Admonitions

!!! note 
    This is a simple note

!!! note "Simple note with custom title"
    This is a simple note with a custom title!

!!! note ""
    This is a not without a title

### Superfences

> Allows adding code to notes

**Example**  
!!! note Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nulla et euismod nulla. Curabitur feugiat, tortor non
consequat finibus, justo purus auctor massa, nec semper lorem quam in massa.

    ``` python
    def bubble_sort(items):
        for i in range(len(items)):
            for j in range(len(items) - 1 - i):
                if items[j] > items[j + 1]:
                    items[j], items[j + 1] = items[j + 1], items[j]
    ```

    Nunc eu odio eleifend, blandit leo a, volutpat sapien. Phasellus posuere in
    sem ut cursus. Nullam sit amet tincidunt ipsum, sit amet elementum turpis.
    Etiam ipsum quam, mattis in purus vitae, lacinia fermentum enim.

### Collapsable

??? note "Closed by default"
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nulla et euismod nulla. Curabitur feugiat, tortor non consequat
    finibus, justo purus auctor massa, nec semper lorem quam in massa.

???+ note "Open by default"
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nulla et euismod nulla. Curabitur feugiat, tortor non consequat 
    finibus, justo purus auctor massa, nec semper lorem quam in massa.

### Inline blocks

!!! info inline end 
    De volgorde van tekst en note is belangrijk om alles correct naast elkaar te krijgen. Maar het lukt dus wel!

```
!!! info inline end
    De volgorde van tekst 
    en note is belangrijk 
    om alles correct naast 
    elkaar te krijgen.
    
    Maar het lukt dus wel!
```

!!! info inline 
    De volgorde van tekst en note is belangrijk om alles correct naast elkaar te krijgen. Maar het lukt dus wel!

```
!!! info inline
    De volgorde van tekst 
    en note is belangrijk 
    om alles correct naast 
    elkaar te krijgen.
    
    Maar het lukt dus wel!
```

### All types

https://squidfunk.github.io/mkdocs-material/reference/admonitions/#supported-types

## Buttons ~ attribute list

> When the Attribute List extension is enabled, any clickable element can be converted into a button by adding the .md-button CSS class, which will receive the selected primary color.
>
> You can add any other class too...

[Go to Google](https://www.google.com){: .md-button }

[Search Obelisk on Google](https://www.google.com/search?q=obelisk+idlab){: .md-button .md-button--primary }

[:fontawesome-solid-paper-plane: Fly to abbreviations](#abbreviations){: .md-button .md-button--primary }

## Code blocks

``` python
def bubble_sort(items):
    for i in range(len(items)):
        for j in range(len(items) - 1 - i):
            if items[j] > items[j + 1]:
                items[j], items[j + 1] = items[j + 1], items[j]
```

### Line numbers

``` python linenums="1"
def bubble_sort(items):
    for i in range(len(items)):
        for j in range(len(items) - 1 - i):
            if items[j] > items[j + 1]:
                items[j], items[j + 1] = items[j + 1], items[j]
```

``` python linenums="4"
            if items[j] > items[j + 1]:
                items[j], items[j + 1] = items[j + 1], items[j]
```

``` python
def bubble_sort(items):
    for i in range(len(items)):
        for j in range(len(items) - 1 - i):
            if items[j] > items[j + 1]:
                items[j], items[j + 1] = items[j + 1], items[j]
```

``` python linenums="1"
def bubble_sort(items):
    for i in range(len(items)):
        for j in range(len(items) - 1 - i):
            if items[j] > items[j + 1]:
                items[j], items[j + 1] = items[j + 1], items[j]
```

### Highlights

``` python hl_lines="2 3"
def bubble_sort(items):
    for i in range(len(items)):
        for j in range(len(items) - 1 - i):
            if items[j] > items[j + 1]:
                items[j], items[j + 1] = items[j + 1], items[j]
```

``` python linenums="5" hl_lines="2 3"
def bubble_sort(items):
    for i in range(len(items)):
        for j in range(len(items) - 1 - i):
            if items[j] > items[j + 1]:
                items[j], items[j + 1] = items[j + 1], items[j]
```

### InlineHilite

The `#!python range()` function is used to generate a sequence of numbers.

The `#!css :hover()` selecter is used to for hovering behaviour styling.

### Keys

++ctrl+alt+del++ to go nuts

++ctrl+f++ to search

## Footnotes

Lorem ipsum[^1] dolor sit amet, consectetur adipiscing elit.[^2]

[^1]: Lorem ipsum dolor sit amet, consectetur adipiscing elit.

[^2]:
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nulla et euismod nulla. Curabitur feugiat, tortor non consequat
finibus, justo purus auctor massa, nec semper lorem quam in massa.

## Smilies and icons

:smile:
:sweat:
:rofl:

:fontawesome-solid-user:
:fontawesome-solid-users:
:fontawesome-solid-tachometer-alt:

## Images

![globe](https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQqi7lpnqg0qzQI_iWmPcrI6L3PVVn-CtfEaw&usqp=CAU){:
align=right} This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of
a globe. This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe. This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe. This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe. This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe.

![globe](https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQqi7lpnqg0qzQI_iWmPcrI6L3PVVn-CtfEaw&usqp=CAU){:
align=left} This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe. This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe. This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe. This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe. This is an image of a globe. This is an image of a globe. This is an image of a globe. This is an image of a
globe.

<figure>
  <img src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQqi7lpnqg0qzQI_iWmPcrI6L3PVVn-CtfEaw&usqp=CAU" width="300" />
  <figcaption>Image with caption, size and centered</figcaption>
</figure>

## Definition list

`Lorem ipsum dolor sit amet`
:   Sed sagittis eleifend rutrum. Donec vitae suscipit est. Nullam tempus tellus non sem sollicitudin, quis rutrum leo
facilisis.

`Cras arcu libero`
:   Aliquam metus eros, pretium sed nulla venenatis, faucibus auctor ex. Proin ut eros sed sapien ullamcorper consequat.
Nunc ligula ante.

    Duis mollis est eget nibh volutpat, fermentum aliquet dui mollis.
    Nam vulputate tincidunt fringilla.
    Nullam dignissim ultrices urna non auctor.

## Non-interactive checklist

- [x] Lorem ipsum dolor sit amet, consectetur adipiscing elit
- [ ] Vestibulum convallis sit amet nisi a tincidunt
    * [x] In hac habitasse platea dictumst
    * [x] In scelerisque nibh non dolor mollis congue sed et metus
    * [ ] Praesent sed risus massa
- [ ] Aenean pretium efficitur erat, donec pharetra, ligula non scelerisque

## Meta tags

### Custom page title per page

```
---
title: Lorem ipsum dolor sit amet
---
```

This will set the title tag inside the document head for the current page to the provided value. Note that the site
title is appended using a dash as a separator, which is the default behavior.

## Smart symbols

| type | result|
| ---- | ----- |
| `(tm)`  | (tm)|
| `(c)`  | (c)|
| `(r)`  | (r)|
| `c/o`  | c/o|
| `+/-`  | +/-|
| `-->`  | -->|
| `<--`  | <--|
| `<-->`  | <-->|
| `=/=`  | =/=|
| `1/4 2/3`  | 1/4 2/3|
| `1st 2nd`  | 1st 2nd|

## Underline

Underline ^^me please^^, thank you.

This won't ^^ work though ^^, ok!

## Superscript

H^2^0

About spaces^they\ must\ be\ escaped^

## Subscript

CH~3~CH~2~OH

text~with\ space~

## Strikethrough

Dit is een ~~vout~~ fout.

## Sorting tables (customized)

Default was all tables sortable.

**We've tweaked the behaviour**, if one of the headers has the `{: .sortable}` affix added, the whole table is sortable

Default now is: non-sortable

```
| Method      | Description                          |
| ----------- | ------------------------------------ |
| `GET`       | :material-check:     Fetch resource  |
| `PUT`       | :material-check-all: Update resource |
| `DELETE`    | :material-close:     Delete resource |
```

| Method      | Description                          |
| ----------- | ------------------------------------ |
| `GET`       | :material-check:     Fetch resource  |
| `PUT`       | :material-check-all: Update resource |
| `DELETE`    | :material-close:     Delete resource |

```
| Method {: .sortable} | Description                 |
| ----------- | ------------------------------------ |
| `GET`       | :material-check:     Fetch resource  |
| `PUT`       | :material-check-all: Update resource |
| `DELETE`    | :material-close:     Delete resource |
```

| Method {: .sortable} | Description                 |
| ----------- | ------------------------------------ |
| `GET`       | :material-check:     Fetch resource  |
| `PUT`       | :material-check-all: Update resource |
| `DELETE`    | :material-close:     Delete resource |

### Sorting types

Autodetection and support for sorting:

* strings
* numbers
* dot seperated strings (eg. ip adressses, versions)
* dates (dd/mm/yy or dd-mm-yy format. Years can be 4 digits. Days and Months can be 1 or 2 digits.)

| Date  | version        |  Size {: .sortable}   |
| ----------- | ------------------------------------ | --- |
| 15/01/2000 | 0.1.2 | 15   | 
| 15/02/1987 | 1.2.36 | 2  |
| 14/12/1999    |  1.2.15| 1238|
| 13/2/2021      | 1.2.9 | 1024  |
| 4/8/2020    | 0.0.1 | 165  |
| 7/7/2020   | 0.0.1 | 165  |
| 12/11/2019     | 10.26.3| 2  |
| 12/12/2019     | 0.5.6 | 1568  |
| 11/12/2019| 4.5.68 | 15  |
| 20/12/2021 | 4.5.9 | 14  |
| 10/10/2010  | 4.25.5 | 24  |
| 5/5/2021 | 1.5.5| 240  |

## Variables

### Custom variables

The url of {{extra.obelisk.name}} is {{extra.obelisk.url}}

### Using variables in snippets

Read: https://squidfunk.github.io/mkdocs-material/reference/variables/#using-variables-in-snippets

### Built-in macro variables

{{ macros_info() }}