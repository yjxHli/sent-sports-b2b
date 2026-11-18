/**
 * E2E 共用：现场挑一个「有货」的商品规格（下单类用例的第一步）。
 *
 * <p><b>为什么不能写死商品/SKU</b>：本套 E2E 会真的下单，演示库存被反复买空——
 * `E2E_PRODUCT_ID=1001` 跑了几十轮之后，两个规格的 `sku_stock` 变成 physical=locked（可售 0），
 * 详情页连一个可点规格都没有，用例就死在「商品页应有可售规格」上，看起来像功能坏了。
 * 已经栽过三次（custom-draft-image / refund-apply+full-flow / checkout-address+core-flow），
 * 新写下单用例请直接用这个 helper。</p>
 *
 * <p>`E2E_PRODUCT_ID` 仍然保留为显式覆盖：需要固定商品做排查时设一下即可。</p>
 */
import type { APIRequestContext } from '@playwright/test';

const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');

export interface PickedSku {
  productId: string;
  skuId: string;
  /** 规格文案（详情页 `.spec-item` 上的文字，用于精确定位按钮） */
  spec: string;
  /** 当前可售数量 */
  stockQty: number;
}

async function getJson(request: APIRequestContext, path: string, token?: string): Promise<any> {
  const res = await request.get(`${API_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

/**
 * @param need 需要的可售数量（普通下单 1~2；UV 定制按 30 件起订传 30）
 */
export async function pickSellableSku(
  request: APIRequestContext,
  need = 1,
  token?: string,
): Promise<PickedSku> {
  const fixedId = process.env.E2E_PRODUCT_ID;
  if (fixedId) {
    const detail = await getJson(request, `/products/${fixedId}`, token);
    const hit = (detail?.data?.skus ?? []).find(
      (s: any) => s.onShelf !== false && Number(s.stockQty) >= need,
    );
    if (hit) {
      return {
        productId: String(fixedId), skuId: String(hit.skuId),
        spec: String(hit.spec), stockQty: Number(hit.stockQty),
      };
    }
    throw new Error(`E2E_PRODUCT_ID=${fixedId} 已无 ≥${need} 的可售规格，去掉该环境变量让用例自己挑`);
  }

  const list = await getJson(request, '/products?page=1&size=20', token);
  for (const product of list?.data?.list ?? []) {
    const detail = await getJson(request, `/products/${product.productId}`, token);
    const hit = (detail?.data?.skus ?? []).find(
      (s: any) => s.onShelf !== false && Number(s.stockQty) >= need,
    );
    if (hit) {
      return {
        productId: String(product.productId), skuId: String(hit.skuId),
        spec: String(hit.spec), stockQty: Number(hit.stockQty),
      };
    }
  }
  throw new Error(
    `演示库里找不到可售 ≥${need} 的规格：先补演示库存（或减少 need），别写死商品 id 硬跑`,
  );
}

/**
 * 挑一个「低库存」规格（0 < 可售 < maxStock），用于「定制起订量 > 可售 → 必须拦截」这类断言。
 *
 * <p>找不到返回 null：这属于演示数据形态问题（全库都是充足库存或全都卖光），
 * 由调用方决定 `test.skip` 还是直接失败，而不是在这里抛错。</p>
 */
export async function pickLowStockSku(
  request: APIRequestContext,
  maxStock = 30,
  token?: string,
): Promise<PickedSku | null> {
  const list = await getJson(request, '/products?page=1&size=20', token);
  for (const product of list?.data?.list ?? []) {
    const detail = await getJson(request, `/products/${product.productId}`, token);
    const hit = (detail?.data?.skus ?? []).find((s: any) => {
      const qty = Number(s.stockQty);
      return s.onShelf !== false && qty > 0 && qty < maxStock;
    });
    if (hit) {
      return {
        productId: String(product.productId), skuId: String(hit.skuId),
        spec: String(hit.spec), stockQty: Number(hit.stockQty),
      };
    }
  }
  return null;
}
