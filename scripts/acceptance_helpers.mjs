/** Helpers for aa_final_acceptance.mjs — dual group-by + saved-query suite. */

export function dualGroupByValidRequest() {
  return {
    sourceCriteria: [
      {
        catalog: "iceberg",
        schema: "srse",
        table: "beneficiary",
        column: "id",
        fuzzyThresholdPercent: null,
      },
    ],
    targetCriteria: [
      {
        catalog: "iceberg_silver",
        schema: "silver_txn",
        table: "tbl_txn_bankdtl",
        column: "m_id",
        fuzzyThresholdPercent: null,
      },
    ],
    sourceDisplayColumns: [],
    highlightDuplicates: false,
    dedup: null,
    singleSource: false,
    groupByColumns: [{ catalog: "iceberg", schema: "srse", table: "beneficiary", column: "district" }],
    aggregates: [{ function: "COUNT", distinct: false }],
  };
}

export function dualGroupByInvalidComparisonRequest() {
  const req = dualGroupByValidRequest();
  req.comparisonGroups = [
    {
      source: [
        {
          catalog: "iceberg",
          schema: "srse",
          table: "beneficiary",
          column: "district",
          fuzzyThresholdPercent: null,
        },
      ],
      target: [
        {
          catalog: "iceberg_silver",
          schema: "silver_txn",
          table: "tbl_txn_bankdtl",
          column: "m_id",
          fuzzyThresholdPercent: null,
        },
      ],
      mode: "COMBINE",
      fuzzyThresholdPercent: null,
      separator: " ",
    },
  ];
  return req;
}
