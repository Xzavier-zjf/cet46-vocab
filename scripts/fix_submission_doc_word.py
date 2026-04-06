from pathlib import Path

import win32com.client
from win32com.client import constants


DOCX_PATH = Path(r"D:/zjf20/Downloads/毕业设计（论文）-提交版.docx")
PDF_PATH = Path(r"D:/zjf20/Downloads/毕业设计（论文）-提交版.pdf")
SCHOOL_HEADER = "广州城市理工学院本科毕业设计（论文）"


TEST_CASES = [
    ("TC-01", "用户注册与风格初始化", "进入注册页，填写用户名与密码并完成三道风格题", "用户成功注册并跳转到初始化页面，风格偏好写入用户资料"),
    ("TC-02", "词库浏览与加入学习", "已登录普通用户，进入词库列表页", "可按词库和词性筛选单词，并将目标单词加入学习计划"),
    ("TC-03", "单词详情 AI 内容生成", "已在词库页点击进入单词详情", "系统先展示基础释义，再异步补齐例句、助记和扩展解释"),
    ("TC-04", "复习任务读取与评分提交", "存在待复习单词，进入复习页", "系统正确加载当日卡片，提交评分后更新下次复习时间和学习统计"),
    ("TC-05", "模拟测验提交与历史查看", "进入测验页并选择题量、模式和词库", "提交后返回得分和错题信息，历史记录可查看当次详情"),
    ("TC-06", "学习助手问答", "登录后进入学习助手页并输入问题", "系统返回与当前问题相关的解释或建议，异常时可回退到可用模型"),
]


def find_range(doc, text):
    rng = doc.Content
    find = rng.Find
    find.ClearFormatting()
    find.Text = text
    if find.Execute():
        return rng
    raise RuntimeError(f"Text not found: {text}")


def set_footer_with_page_number(footer, style, restart=None, start=None, size=10.5):
    footer.LinkToPrevious = False
    footer.Range.Text = ""
    footer.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    footer.Range.Font.NameFarEast = "宋体"
    footer.Range.Font.Name = "Times New Roman"
    footer.Range.Font.Size = size
    page_numbers = footer.PageNumbers
    if restart is not None:
        page_numbers.RestartNumberingAtSection = restart
    if start is not None:
        page_numbers.StartingNumber = start
    page_numbers.NumberStyle = style
    page_numbers.Add(constants.wdAlignParagraphCenter, True)
    footer.Range.Font.NameFarEast = "宋体"
    footer.Range.Font.Name = "Times New Roman"
    footer.Range.Font.Size = size


def set_header(section):
    section.PageSetup.OddAndEvenPagesHeaderFooter = True
    odd_header = section.Headers(constants.wdHeaderFooterPrimary)
    odd_header.LinkToPrevious = False
    odd_header.Range.Text = ""
    odd_header.Range.Fields.Add(odd_header.Range, constants.wdFieldStyleRef, "Heading 1")
    odd_header.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    odd_header.Range.Font.NameFarEast = "宋体"
    odd_header.Range.Font.Name = "Times New Roman"
    odd_header.Range.Font.Size = 10.5

    even_header = section.Headers(constants.wdHeaderFooterEvenPages)
    even_header.LinkToPrevious = False
    even_header.Range.Text = SCHOOL_HEADER
    even_header.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    even_header.Range.Font.NameFarEast = "宋体"
    even_header.Range.Font.Name = "Times New Roman"
    even_header.Range.Font.Size = 10.5


def fix_page_numbers(doc):
    if doc.Sections.Count < 3:
        raise RuntimeError("Unexpected section count")

    # Section 2: abstract and TOC, Roman numerals, same odd/even footer size
    sec2 = doc.Sections(2)
    sec2.PageSetup.OddAndEvenPagesHeaderFooter = True
    sec2.Headers(constants.wdHeaderFooterPrimary).Range.Text = ""
    sec2.Headers(constants.wdHeaderFooterEvenPages).Range.Text = ""
    set_footer_with_page_number(sec2.Footers(constants.wdHeaderFooterPrimary), constants.wdPageNumberStyleLowercaseRoman, restart=True, start=1, size=10.5)
    set_footer_with_page_number(sec2.Footers(constants.wdHeaderFooterEvenPages), constants.wdPageNumberStyleLowercaseRoman, restart=True, start=1, size=10.5)

    # Section 3: body, Arabic numerals, same odd/even footer size and continuous within body
    sec3 = doc.Sections(3)
    set_header(sec3)
    set_footer_with_page_number(sec3.Footers(constants.wdHeaderFooterPrimary), constants.wdPageNumberStyleArabic, restart=True, start=1, size=10.5)
    set_footer_with_page_number(sec3.Footers(constants.wdHeaderFooterEvenPages), constants.wdPageNumberStyleArabic, restart=True, start=1, size=10.5)


def insert_test_case_table(doc, word):
    try:
        find_range(doc, "表6-1 典型功能测试用例")
        return
    except RuntimeError:
        pass

    target = find_range(doc, "6.4 部署过程与运行支撑")
    insert_at = target.Start
    selection = word.Selection
    selection.SetRange(insert_at, insert_at)

    selection.TypeText("为使测试章节具备更明确的可复查性，结合当前仓库中的测试说明文档、自动化回归结果和实际运行页面，选取具有代表性的功能测试用例如表6-1所示。")
    selection.TypeParagraph()
    selection.TypeText("表6-1 典型功能测试用例")
    selection.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    selection.Font.NameFarEast = "宋体"
    selection.Font.Name = "Times New Roman"
    selection.Font.Size = 10.5
    selection.TypeParagraph()

    table = doc.Tables.Add(selection.Range, len(TEST_CASES) + 1, 4)
    table.Borders.Enable = True
    table.Range.Font.NameFarEast = "宋体"
    table.Range.Font.Name = "Times New Roman"
    table.Range.Font.Size = 10.5
    table.Range.ParagraphFormat.LineSpacingRule = constants.wdLineSpaceExactly
    table.Range.ParagraphFormat.LineSpacing = 20
    table.Range.ParagraphFormat.Alignment = constants.wdAlignParagraphCenter
    table.AllowAutoFit = True

    headers = ["编号", "测试内容", "测试步骤", "预期结果"]
    for col, title in enumerate(headers, start=1):
        table.Cell(1, col).Range.Text = title
        table.Cell(1, col).Range.Bold = True

    for row_idx, row in enumerate(TEST_CASES, start=2):
        table.Cell(row_idx, 1).Range.Text = row[0]
        table.Cell(row_idx, 2).Range.Text = row[1]
        table.Cell(row_idx, 3).Range.Text = f"前置条件：{row[2]}"
        table.Cell(row_idx, 4).Range.Text = row[3]

    # Remove left/right vertical borders is cumbersome in COM; keep standard grid for readability.
    selection.SetRange(table.Range.End, table.Range.End)
    selection.TypeParagraph()
    selection.TypeText("从表6-1可以看出，测试章节不仅给出了自动化回归与构建结果，还补充了围绕注册、词库浏览、AI 生成、复习、测验和学习助手等核心业务流程的典型功能测试项，能够更完整地支撑论文对系统可用性的说明。")
    selection.TypeParagraph()


def main():
    if not DOCX_PATH.exists():
        raise FileNotFoundError(DOCX_PATH)

    word = win32com.client.gencache.EnsureDispatch("Word.Application")
    word.Visible = False
    word.DisplayAlerts = 0
    try:
        doc = word.Documents.Open(str(DOCX_PATH))
        fix_page_numbers(doc)
        insert_test_case_table(doc, word)
        if doc.TablesOfContents.Count >= 1:
            doc.TablesOfContents(1).Update()
        doc.Fields.Update()
        doc.Save()
        doc.SaveAs(str(PDF_PATH), FileFormat=constants.wdFormatPDF)
        print(f"DOCX={DOCX_PATH}")
        print(f"PDF={PDF_PATH}")
        print(f"SECTIONS={doc.Sections.Count}")
        print(f"INLINE_SHAPES={doc.InlineShapes.Count}")
        doc.Close(False)
    finally:
        word.Quit()


if __name__ == "__main__":
    main()
