# C-to-AArch64 Assembly Compiler Kernel (V2 - using CSV input arrays)
# Uses global arrays from CSV inputs, declares only scratch arrays

# Token codes
TK_EOF = 0
TK_IF = 2
TK_ELSE = 3
TK_FOR = 4
TK_INT = 5
TK_LBRACE = 6
TK_RBRACE = 7
TK_LPAREN = 8
TK_RPAREN = 9
TK_SEMI = 10
TK_EQ = 11
TK_EQEQ = 12
TK_LT = 13
TK_GT = 22
TK_LTEQ = 23
TK_GTEQ = 24
TK_NOTEQ = 25
TK_PLUS = 14
TK_MINUS = 15
TK_STAR = 16
TK_SLASH = 17
TK_PLUSPLUS = 18
TK_IDENT = 20
TK_NUMBER = 21

# AST node types
NODE_VAR_DECL = 2
NODE_ASSIGN = 3
NODE_IF = 4
NODE_FOR = 5
NODE_BINOP = 6
NODE_IDENT = 7
NODE_NUMBER = 8
NODE_POSTINC = 9

# Binary op codes
OP_ADD = 1
OP_SUB = 2
OP_MUL = 3
OP_DIV = 4
OP_EQEQ = 5
OP_LT = 6
OP_GT = 7
OP_LTEQ = 8
OP_GTEQ = 9
OP_NOTEQ = 10

# Output opcodes
OP_MOV_REG = 1
OP_MOV_IMM = 2
OP_ADD_R = 3
OP_SUB_R = 4
OP_MUL_R = 5
OP_SDIV_R = 6
OP_CMP = 7
OP_B = 8
OP_BEQ = 9
OP_BNE = 10
OP_BLT = 11
OP_BGT = 12
OP_BLE = 13
OP_BGE = 14
OP_LABEL = 17
OP_INC = 18

# AArch64 registers
X0 = 0
X1 = 1
X2 = 2

# Scratch arrays (allocated here, NOT used as CSVs)
node_type = [0 for _ in range(512)]
node_child1 = [0 for _ in range(512)]
node_child2 = [0 for _ in range(512)]
node_child3 = [0 for _ in range(512)]
node_val = [0 for _ in range(512)]
node_next = [0 for _ in range(512)]
node_extra = [0 for _ in range(512)]
sym_name_hash = [0 for _ in range(64)]
sym_reg = [0 for _ in range(64)]
label_addr = [0 for _ in range(256)]
label_fwd_op = [0 for _ in range(256)]
label_fwd_id = [0 for _ in range(256)]
parse_state = [0 for _ in range(16)]
gen_state = [0 for _ in range(8)]


# tokens = [0 for _ in range(2048)]
# token_vals = [0 for _ in range(2048)]
# meta = [0 for _ in range(8)]
# out_op = [0 for _ in range(2048)]
# out_dst = [0 for _ in range(2048)]
# out_src = [0 for _ in range(2048)]

def lookup_sym(ident_hash):
    found = -1
    i = 0
    while i < parse_state[2]:
        if sym_name_hash[i] == ident_hash:
            found = i
        i = i + 1
    return found

def emit(op, dst, src):
    idx = gen_state[0]
    out_op[idx] = op
    out_dst[idx] = dst
    out_src[idx] = src
    gen_state[0] = idx + 1

def alloc_label():
    lc = gen_state[1]
    gen_state[1] = lc + 1
    return lc

def emit_label(label_id):
    idx = gen_state[0]
    label_addr[label_id] = idx
    out_op[idx] = OP_LABEL
    out_dst[idx] = label_id
    out_src[idx] = 0
    gen_state[0] = idx + 1

def emit_jump(jmp_op, label_id):
    idx = gen_state[0]
    out_op[idx] = jmp_op
    out_dst[idx] = label_id
    out_src[idx] = 0
    fc = gen_state[2]
    label_fwd_op[fc] = idx
    label_fwd_id[fc] = label_id
    gen_state[2] = fc + 1
    gen_state[0] = idx + 1

def get_cond_op(cond_node):
    result = 0
    if node_type[cond_node] == NODE_BINOP:
        result = node_val[cond_node]
    return result

def invert_jmp(op_code):
    result = OP_B
    if op_code == OP_EQEQ:
        result = OP_BNE
    if op_code == OP_LT:
        result = OP_BGE
    if op_code == OP_GT:
        result = OP_BLE
    if op_code == OP_LTEQ:
        result = OP_BGT
    if op_code == OP_GTEQ:
        result = OP_BLT
    if op_code == OP_NOTEQ:
        result = OP_BEQ
    return result

def parse_primary():
    p = parse_state[0]
    tk = tokens[p]
    result = -1

    if tk == TK_IDENT:
        ident_hash = token_vals[p]
        sym_idx = lookup_sym(ident_hash)
        parse_state[0] = p + 1
        nc = parse_state[1]
        node_type[nc] = NODE_IDENT
        node_val[nc] = sym_idx
        node_child1[nc] = -1
        node_child2[nc] = -1
        node_child3[nc] = -1
        node_next[nc] = -1
        parse_state[1] = nc + 1
        result = nc

    if tk == TK_NUMBER:
        val = token_vals[p]
        parse_state[0] = p + 1
        nc = parse_state[1]
        node_type[nc] = NODE_NUMBER
        node_val[nc] = val
        node_child1[nc] = -1
        node_child2[nc] = -1
        node_child3[nc] = -1
        node_next[nc] = -1
        parse_state[1] = nc + 1
        result = nc

    if tk == TK_LPAREN:
        parse_state[0] = p + 1
        result = parse_expr()
        parse_state[0] = parse_state[0] + 1

    return result

def parse_mul():
    left = parse_primary()
    done = 0

    while done < 1:
        p = parse_state[0]
        tk = tokens[p]
        op = 0

        if tk == TK_STAR:
            op = OP_MUL
        if tk == TK_SLASH:
            op = OP_DIV

        if op > 0:
            parse_state[0] = p + 1
            right = parse_primary()
            nc = parse_state[1]
            node_type[nc] = NODE_BINOP
            node_child1[nc] = left
            node_child2[nc] = right
            node_val[nc] = op
            node_child3[nc] = -1
            node_next[nc] = -1
            parse_state[1] = nc + 1
            left = nc

        if op < 1:
            done = 1

    return left

def parse_add():
    left = parse_mul()
    done = 0

    while done < 1:
        p = parse_state[0]
        tk = tokens[p]
        op = 0

        if tk == TK_PLUS:
            op = OP_ADD
        if tk == TK_MINUS:
            op = OP_SUB

        if op > 0:
            parse_state[0] = p + 1
            right = parse_mul()
            nc = parse_state[1]
            node_type[nc] = NODE_BINOP
            node_child1[nc] = left
            node_child2[nc] = right
            node_val[nc] = op
            node_child3[nc] = -1
            node_next[nc] = -1
            parse_state[1] = nc + 1
            left = nc

        if op < 1:
            done = 1

    return left

def parse_comparison():
    left = parse_add()
    p = parse_state[0]
    tk = tokens[p]
    op = 0

    if tk == TK_EQEQ:
        op = OP_EQEQ
    if tk == TK_LT:
        op = OP_LT
    if tk == TK_GT:
        op = OP_GT
    if tk == TK_LTEQ:
        op = OP_LTEQ
    if tk == TK_GTEQ:
        op = OP_GTEQ
    if tk == TK_NOTEQ:
        op = OP_NOTEQ

    if op > 0:
        parse_state[0] = p + 1
        right = parse_add()
        nc = parse_state[1]
        node_type[nc] = NODE_BINOP
        node_child1[nc] = left
        node_child2[nc] = right
        node_val[nc] = op
        node_child3[nc] = -1
        node_next[nc] = -1
        parse_state[1] = nc + 1
        left = nc

    return left

def parse_expr():
    result = parse_comparison()
    return result

def parse_var_decl():
    parse_state[0] = parse_state[0] + 1
    p = parse_state[0]
    ident_hash = token_vals[p]
    parse_state[0] = parse_state[0] + 1

    p2 = parse_state[0]
    tk2 = tokens[p2]
    if tk2 == TK_EQ:
        parse_state[0] = parse_state[0] + 1
        parse_state[0] = parse_state[0] + 1

    parse_state[0] = parse_state[0] + 1

    sc = parse_state[2]
    sym_name_hash[sc] = ident_hash
    next_r = parse_state[3]
    sym_reg[sc] = next_r
    parse_state[2] = sc + 1
    parse_state[3] = next_r + 1

    nc = parse_state[1]
    node_type[nc] = NODE_VAR_DECL
    node_val[nc] = sc
    node_child1[nc] = -1
    node_child2[nc] = -1
    node_child3[nc] = -1
    node_next[nc] = -1
    parse_state[1] = nc + 1

    return nc

def parse_assign():
    p = parse_state[0]
    ident_hash = token_vals[p]
    parse_state[0] = p + 1
    parse_state[0] = parse_state[0] + 1

    expr_node = parse_expr()
    parse_state[0] = parse_state[0] + 1

    sym_idx = lookup_sym(ident_hash)

    nc = parse_state[1]
    node_type[nc] = NODE_ASSIGN
    node_val[nc] = sym_idx
    node_child1[nc] = expr_node
    node_child2[nc] = -1
    node_child3[nc] = -1
    node_next[nc] = -1
    parse_state[1] = nc + 1

    return nc

def parse_incr():
    p = parse_state[0]
    ident_hash = token_vals[p]
    parse_state[0] = p + 1

    p2 = parse_state[0]
    tk2 = tokens[p2]

    if tk2 == TK_PLUSPLUS:
        parse_state[0] = p2 + 1
        sym_idx = lookup_sym(ident_hash)
        nc = parse_state[1]
        node_type[nc] = NODE_POSTINC
        node_val[nc] = sym_idx
        node_child1[nc] = -1
        node_child2[nc] = -1
        node_child3[nc] = -1
        node_next[nc] = -1
        parse_state[1] = nc + 1
        return nc

    if tk2 == TK_EQ:
        parse_state[0] = p2 + 1
        sym_idx = lookup_sym(ident_hash)
        expr_node = parse_expr()

        nc = parse_state[1]
        node_type[nc] = NODE_ASSIGN
        node_val[nc] = sym_idx
        node_child1[nc] = expr_node
        node_child2[nc] = -1
        node_child3[nc] = -1
        node_next[nc] = -1
        parse_state[1] = nc + 1
        return nc

    return -1

def parse_if():
    parse_state[0] = parse_state[0] + 1
    parse_state[0] = parse_state[0] + 1

    cond_node = parse_expr()
    parse_state[0] = parse_state[0] + 1
    parse_state[0] = parse_state[0] + 1

    then_node = parse_block()
    parse_state[0] = parse_state[0] + 1

    else_node = -1
    p = parse_state[0]
    if tokens[p] == TK_ELSE:
        parse_state[0] = parse_state[0] + 1
        parse_state[0] = parse_state[0] + 1
        else_node = parse_block()
        parse_state[0] = parse_state[0] + 1

    nc = parse_state[1]
    node_type[nc] = NODE_IF
    node_child1[nc] = cond_node
    node_child2[nc] = then_node
    node_child3[nc] = else_node
    node_next[nc] = -1
    parse_state[1] = nc + 1

    return nc

def parse_for():
    parse_state[0] = parse_state[0] + 1
    parse_state[0] = parse_state[0] + 1

    init_node = parse_statement()
    cond_node = parse_expr()
    parse_state[0] = parse_state[0] + 1

    incr_node = parse_incr()
    parse_state[0] = parse_state[0] + 1
    parse_state[0] = parse_state[0] + 1

    body_node = parse_block()
    parse_state[0] = parse_state[0] + 1

    nc = parse_state[1]
    node_type[nc] = NODE_FOR
    node_child1[nc] = init_node
    node_child2[nc] = cond_node
    node_child3[nc] = body_node
    node_extra[nc] = incr_node
    node_next[nc] = -1
    parse_state[1] = nc + 1

    return nc

def parse_block():
    first_node = -1
    prev_node = -1
    done = 0

    while done < 1:
        p = parse_state[0]
        tk = tokens[p]

        if tk == TK_RBRACE:
            done = 1
        if tk < 0.5:
            done = 1

        if done < 1:
            stmt_node = parse_statement()

            if first_node < 0:
                first_node = stmt_node

            if prev_node >= 0:
                node_next[prev_node] = stmt_node

            prev_node = stmt_node

    return first_node

def parse_statement():
    p = parse_state[0]
    tk = tokens[p]
    result = -1

    if tk == TK_INT:
        result = parse_var_decl()

    if tk == TK_IF:
        result = parse_if()

    if tk == TK_FOR:
        result = parse_for()

    if tk == TK_IDENT:
        result = parse_assign()

    return result

def parse_program():
    first_node = -1
    prev_node = -1
    done = 0

    while done < 1:
        p = parse_state[0]
        tk = tokens[p]

        if tk < 0.5:
            done = 1

        if done < 1:
            stmt_node = parse_statement()

            if first_node < 0:
                first_node = stmt_node

            if prev_node >= 0:
                node_next[prev_node] = stmt_node

            prev_node = stmt_node

    return first_node

def gen_expr(node):
    nt = node_type[node]

    if nt == NODE_NUMBER:
        val = node_val[node]
        emit(OP_MOV_IMM, X0, val)

    if nt == NODE_IDENT:
        sym_idx = node_val[node]
        reg = sym_reg[sym_idx]
        emit(OP_MOV_REG, X0, reg)

    if nt == NODE_BINOP:
        op_code = node_val[node]
        left_node = node_child1[node]
        right_node = node_child2[node]

        gen_expr(left_node)
        emit(OP_MOV_REG, X1, X0)
        gen_expr(right_node)

        if op_code == OP_ADD:
            emit(OP_ADD_R, X1, X0)
            emit(OP_MOV_REG, X0, X1)

        if op_code == OP_SUB:
            emit(OP_SUB_R, X1, X0)
            emit(OP_MOV_REG, X0, X1)

        if op_code == OP_MUL:
            emit(OP_MUL_R, X1, X0)
            emit(OP_MOV_REG, X0, X1)

        if op_code == OP_DIV:
            emit(OP_SDIV_R, X1, X0)
            emit(OP_MOV_REG, X0, X1)

        if op_code == OP_EQEQ:
            emit(OP_CMP, X1, X0)
        if op_code == OP_LT:
            emit(OP_CMP, X1, X0)
        if op_code == OP_GT:
            emit(OP_CMP, X1, X0)
        if op_code == OP_LTEQ:
            emit(OP_CMP, X1, X0)
        if op_code == OP_GTEQ:
            emit(OP_CMP, X1, X0)
        if op_code == OP_NOTEQ:
            emit(OP_CMP, X1, X0)

def gen_if(node):
    cond_node = node_child1[node]
    then_node = node_child2[node]
    else_node = node_child3[node]

    gen_expr(cond_node)

    cond_op_code = get_cond_op(cond_node)

    label_else = alloc_label()
    label_end = alloc_label()

    jmp_op = invert_jmp(cond_op_code)
    emit_jump(jmp_op, label_else)

    gen_stmt(then_node)
    emit_jump(OP_B, label_end)

    emit_label(label_else)
    if else_node >= 0:
        gen_stmt(else_node)

    emit_label(label_end)

def gen_for(node):
    init_node = node_child1[node]
    cond_node = node_child2[node]
    body_node = node_child3[node]
    incr_node = node_extra[node]

    label_loop = alloc_label()
    label_end = alloc_label()

    gen_stmt(init_node)

    emit_label(label_loop)

    gen_expr(cond_node)
    cond_op_code = get_cond_op(cond_node)
    jmp_op = invert_jmp(cond_op_code)
    emit_jump(jmp_op, label_end)

    gen_stmt(body_node)

    gen_stmt(incr_node)

    emit_jump(OP_B, label_loop)

    emit_label(label_end)

def gen_stmt(node):
    if node < 0:
        return

    nt = node_type[node]

    if nt == NODE_VAR_DECL:
        sym_idx = node_val[node]
        reg = sym_reg[sym_idx]
        emit(OP_MOV_IMM, reg, 0)

    if nt == NODE_ASSIGN:
        expr_node = node_child1[node]
        gen_expr(expr_node)
        sym_idx = node_val[node]
        reg = sym_reg[sym_idx]
        emit(OP_MOV_REG, reg, X0)

    if nt == NODE_POSTINC:
        sym_idx = node_val[node]
        reg = sym_reg[sym_idx]
        emit(OP_INC, reg, 0)

    if nt == NODE_IF:
        gen_if(node)

    if nt == NODE_FOR:
        gen_for(node)

    next_node = node_next[node]
    if next_node >= 0:
        gen_stmt(next_node)

# Main execution flow

parse_state[0] = 0
parse_state[1] = 0
parse_state[2] = 0
parse_state[3] = 2

root_node = parse_program()

gen_state[0] = 0
gen_state[1] = 0
gen_state[2] = 0

gen_stmt(root_node)

meta[1] = gen_state[0]
